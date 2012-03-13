package com.nearinfinity.blur.manager.writer;

import static com.nearinfinity.blur.lucene.LuceneConstant.LUCENE_VERSION;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NRTManager;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.SearcherWarmer;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NRTCachingDirectory;

import com.nearinfinity.blur.analysis.BlurAnalyzer;
import com.nearinfinity.blur.index.IndexWriter;
import com.nearinfinity.blur.log.Log;
import com.nearinfinity.blur.log.LogFactory;
import com.nearinfinity.blur.thrift.generated.Record;
import com.nearinfinity.blur.thrift.generated.Row;
import com.nearinfinity.blur.utils.PrimeDocCache;

public class BlurNRTIndex extends BlurIndex {

  private static final Log LOG = LogFactory.getLog(BlurNRTIndex.class);
  private static final boolean APPLY_ALL_DELETES = true;

  private NRTManager _nrtManager;
  private SearcherManager _manager;
  private AtomicBoolean _isClosed = new AtomicBoolean();
  private IndexWriter _writer;
  private Thread _committer;

  // externally set
  private BlurAnalyzer _analyzer;
  private Directory _directory;
  private ExecutorService _executorService;
  private String _table;
  private String _shard;
  private long _timeBetweenCommits = TimeUnit.SECONDS.toMillis(60);
  private Similarity _similarity;
  private volatile long _lastRefresh;
  private long _timeBetweenRefreshs = TimeUnit.MILLISECONDS.toMillis(500);
  private double _nrtCachingMaxMergeSizeMB = 60;
  private double _nrtCachingMaxCachedMB = 5.0;
  private Thread _refresher;
  private TransactionRecorder _recorder;
  private Configuration _configuration;

  private SearcherWarmer _warmer = new SearcherWarmer() {
    @Override
    public void warm(IndexSearcher s) throws IOException {
      IndexReader indexReader = s.getIndexReader();
      IndexReader[] subReaders = indexReader.getSequentialSubReaders();
      if (subReaders == null) {
        PrimeDocCache.getPrimeDocBitSet(indexReader);
      } else {
        for (IndexReader reader : subReaders) {
          PrimeDocCache.getPrimeDocBitSet(reader);
        }
      }
    }
  };
  private Path _walPath;

  public void init() throws IOException {
    Path walTablePath = new Path(_walPath, _table);
    Path walShardPath = new Path(walTablePath, _shard);

    IndexWriterConfig conf = new IndexWriterConfig(LUCENE_VERSION, _analyzer);
    conf.setWriteLockTimeout(TimeUnit.MINUTES.toMillis(5));
    conf.setSimilarity(_similarity);
    TieredMergePolicy mergePolicy = (TieredMergePolicy) conf.getMergePolicy();
    mergePolicy.setUseCompoundFile(false);

    NRTCachingDirectory cachingDirectory = new NRTCachingDirectory(_directory, _nrtCachingMaxMergeSizeMB, _nrtCachingMaxCachedMB);
    _writer = new IndexWriter(cachingDirectory, conf);
    _recorder = new TransactionRecorder();
    _recorder.setAnalyzer(_analyzer);
    _recorder.setConfiguration(_configuration);
    _recorder.setWalPath(walShardPath);

    _recorder.init();
    _recorder.replay(_writer);
    
    _nrtManager = new NRTManager(_writer, _executorService, _warmer, APPLY_ALL_DELETES);
    _manager = _nrtManager.getSearcherManager(APPLY_ALL_DELETES);
    _lastRefresh = System.nanoTime();
    startCommiter();
    startRefresher();
  }

  private void startRefresher() {
    _refresher = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!_isClosed.get()) {
          try {
            LOG.debug("Refreshing of [{0}/{1}].", _table, _shard);
            maybeReopen();
          } catch (IOException e) {
            LOG.error("Error during refresh of [{0}/{1}].", _table, _shard, e);
          }
          try {
            Thread.sleep(_timeBetweenRefreshs);
          } catch (InterruptedException e) {
            if (_isClosed.get()) {
              return;
            }
            LOG.error("Unknown error with refresher thread [{0}/{1}].", _table, _shard, e);
          }
        }
      }
    });
    _refresher.setDaemon(true);
    _refresher.setName("Refresh Thread [" + _table + "/" + _shard + "]");
    _refresher.start();
  }

  private void startCommiter() {
    _committer = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!_isClosed.get()) {
          try {
            LOG.info("Committing of [{0}/{1}].", _table, _shard);
            _recorder.commit(_writer);
          } catch (CorruptIndexException e) {
            LOG.error("Error during commit of [{0}/{1}].", _table, _shard, e);
          } catch (IOException e) {
            LOG.error("Error during commit of [{0}/{1}].", _table, _shard, e);
          }
          try {
            Thread.sleep(_timeBetweenCommits);
          } catch (InterruptedException e) {
            if (_isClosed.get()) {
              return;
            }
            LOG.error("Unknown error with committer thread [{0}/{1}].", _table, _shard, e);
          }
        }
      }
    });
    _committer.setDaemon(true);
    _committer.setName("Commit Thread [" + _table + "/" + _shard + "]");
    _committer.start();
  }

  @Override
  public void replaceRow(boolean waitToBeVisible, boolean wal, Row row) throws IOException {
    List<Record> records = row.records;
    if (records == null || records.isEmpty()) {
      deleteRow(waitToBeVisible, wal, row.id);
      return;
    }
    long generation = _recorder.replaceRow(wal, row, _nrtManager);
    waitToBeVisible(waitToBeVisible, generation);
  }

  @Override
  public void deleteRow(boolean waitToBeVisible, boolean wal, String rowId) throws IOException {
    long generation = _recorder.deleteRow(wal, rowId, _nrtManager);
    waitToBeVisible(waitToBeVisible, generation);
  }

  @Override
  public IndexReader getIndexReader() throws IOException {
    IndexSearcher searcher = _manager.acquire();
    return searcher.getIndexReader();
  }

  @Override
  public void close() throws IOException {
    // @TODO make sure that locks are cleaned up.
    _isClosed.set(true);
    _committer.interrupt();
    _refresher.interrupt();
    try {
      _recorder.close();
      _writer.close();
      _manager.close();
      _nrtManager.close();
    } finally {
      _directory.close();
    }
  }

  @Override
  public void refresh() throws IOException {
    _nrtManager.maybeReopen(APPLY_ALL_DELETES);
  }

  @Override
  public AtomicBoolean isClosed() {
    return _isClosed;
  }

  @Override
  public void optimize(int numberOfSegmentsPerShard) throws IOException {
    _writer.forceMerge(numberOfSegmentsPerShard);
  }

  private void waitToBeVisible(boolean waitToBeVisible, long generation) throws IOException {
    if (waitToBeVisible) {
      // if visibility is required then reopen.
      _lastRefresh = System.nanoTime();
      _nrtManager.maybeReopen(true);
      _nrtManager.waitForGeneration(generation, APPLY_ALL_DELETES);
    } else {
      // if not, then check to see if reopened is needed.
      maybeReopen();
    }
  }

  private void maybeReopen() throws IOException {
    if (_lastRefresh + _timeBetweenRefreshs < System.nanoTime()) {
      if (_nrtManager.maybeReopen(true)) {
        _lastRefresh = System.nanoTime();
      }
    }
  }

  public void setAnalyzer(BlurAnalyzer analyzer) {
    _analyzer = analyzer;
  }

  public void setDirectory(Directory directory) {
    _directory = directory;
  }

  public void setExecutorService(ExecutorService executorService) {
    _executorService = executorService;
  }

  public void setTable(String table) {
    _table = table;
  }

  public void setShard(String shard) {
    _shard = shard;
  }

  public void setTimeBetweenCommits(long timeBetweenCommits) {
    _timeBetweenCommits = timeBetweenCommits;
  }

  public void setSimilarity(Similarity similarity) {
    _similarity = similarity;
  }

  public void setTimeBetweenRefreshs(long timeBetweenRefreshs) {
    _timeBetweenRefreshs = timeBetweenRefreshs;
  }

  public void setNrtCachingMaxMergeSizeMB(double nrtCachingMaxMergeSizeMB) {
    _nrtCachingMaxMergeSizeMB = nrtCachingMaxMergeSizeMB;
  }

  public void setNrtCachingMaxCachedMB(int nrtCachingMaxCachedMB) {
    _nrtCachingMaxCachedMB = nrtCachingMaxCachedMB;
  }

  public void setWalPath(Path walPath) {
    _walPath = walPath;
  }

  public void setConfiguration(Configuration configuration) {
    _configuration = configuration;
  }
}