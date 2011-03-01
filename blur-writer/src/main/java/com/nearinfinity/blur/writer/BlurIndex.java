package com.nearinfinity.blur.writer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import com.nearinfinity.blur.analysis.BlurAnalyzer;
import com.nearinfinity.blur.log.Log;
import com.nearinfinity.blur.log.LogFactory;
import com.nearinfinity.blur.lucene.search.FairSimilarity;
import com.nearinfinity.blur.thrift.generated.Row;
import com.nearinfinity.blur.utils.BlurConstants;
import com.nearinfinity.blur.utils.RowIndexWriter;

public class BlurIndex implements Runnable,BlurConstants {
    
    private static final Log LOG =  LogFactory.getLog(BlurIndex.class);
    
    private class BlurIndexMutation {
        volatile Directory directory;
        volatile boolean indexed;
    }
    
    private Directory directory;
    private IndexWriter writer;
    private Thread daemon;
    private BlockingQueue<BlurIndexMutation> mutationQueue;
    private BlurAnalyzer analyzer;
    private int maxNumberOfDirsMergedAtOnce = 16;
    private int maxThreadCountForMerger = 5;
    private int maxBlockingTimePerUpdate = 10;
    
    public void init() throws IOException {
        setupWriter();
        daemon = new Thread(this);
        daemon.setDaemon(true);
        daemon.setName("Blur-Update-Thread-" + directory.toString());
        daemon.start();
        mutationQueue = new ArrayBlockingQueue<BlurIndexMutation>(maxNumberOfDirsMergedAtOnce);
    }
    
    public void close() {
        daemon.interrupt();
    }
    
    public boolean replaceRow(Collection<Row> rows) throws InterruptedException, IOException {
        BlurIndexMutation update = new BlurIndexMutation();
        update.directory = index(rows);
        synchronized (update) {
            mutationQueue.put(update);
            update.wait();
            return update.indexed;
        }
    }
    
    @Override
    public void run() {
        while (true) {
            try {
                updateWriter();
            } catch (Exception e) {
                LOG.error("Unknown error while indexing.",e);
            }
        }
    }
    
    private void setupWriter() throws IOException {
        IndexWriter indexWriter = new IndexWriter(directory, analyzer, MaxFieldLength.UNLIMITED);
        indexWriter.setSimilarity(new FairSimilarity());
        indexWriter.setUseCompoundFile(false);
        ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
        mergeScheduler.setMaxThreadCount(maxThreadCountForMerger);
        indexWriter.setMergeScheduler(mergeScheduler);
    }

    private Directory index(Collection<Row> rows) throws IOException {
        RAMDirectory dir = new RAMDirectory();
        IndexWriter indexWriter = new IndexWriter(dir, analyzer, MaxFieldLength.UNLIMITED);
        indexWriter.setSimilarity(new FairSimilarity());
        indexWriter.setUseCompoundFile(false);
        RowIndexWriter rowIndexWriter = new RowIndexWriter(indexWriter, analyzer);
        for (Row row : rows) {
            rowIndexWriter.replace(row);
        }
        indexWriter.optimize();
        indexWriter.close();
        return dir;
    }

    private void updateWriter() throws IOException, InterruptedException {
        Collection<BlurIndexMutation> mutations = null;
        BlurIndexMutation update = mutationQueue.take();
        try {
            mutations = new HashSet<BlurIndexMutation>();
            mutations.add(update);
            Thread.sleep(maxBlockingTimePerUpdate);
            mutationQueue.drainTo(mutations);
            for (BlurIndexMutation u : mutations) {
                IndexReader reader = IndexReader.open(u.directory);
                TermEnum termEnum = reader.terms(new Term(ROW_ID));
                INNER:
                do {
                    Term term = termEnum.term();
                    if (term != null) {
                        if (!term.field().equals(ROW_ID)) {
                            break INNER;
                        }
                        writer.deleteDocuments(term);
                    }
                } while (termEnum.next());
                termEnum.close();
                reader.close();
            }
            writer.addIndexesNoOptimize(getDirectories(mutations));
            for (BlurIndexMutation mutation : mutations) {
                mutation.indexed = true;
            }
            writer.commit();
        } finally {
            if (mutations != null) {
                for (BlurIndexMutation mutation : mutations) {
                    synchronized (mutation) {
                        mutation.notifyAll();
                    }
                }
            }
        }
    }

    private Directory[] getDirectories(Collection<BlurIndexMutation> mutations) {
        Directory[] dirs = new Directory[mutations.size()];
        int i = 0;
        for (BlurIndexMutation update : mutations) {
            dirs[i++] = update.directory;
        }
        return dirs;
    }

    public void setAnalyzer(BlurAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public IndexReader getIndexReader() throws IOException {
        return writer.getReader();
    }

    public void setDirectory(Directory directory) {
        this.directory = directory;
    }
}