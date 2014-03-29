/*

Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
/*global blurconsole:false */
blurconsole.model = (function() {
	'use strict';
	var
		configMap = {
			poller : null
		},
		stateMap = {
			tableNameMap: null,
			nodeMap : null,
			queryPerformance : [],
			queries : {}
		},
		isFakeData = true,
		tables, metrics, nodes, queries, initModule, nodePoller, tablePoller, queryPerformancePoller, queryPoller;

	tables = (function() {
		var getClusters, getEnabledTables, getDisabledTables, isDataLoaded, disableTable, enableTable, deleteTable, getSchema, findTerms;

		getClusters = function() {
			if (stateMap.tableNameMap === null) {
				return [];
			}

			return blurconsole.utils.unique($.map(stateMap.tableNameMap, function(table){
				return table.cluster;
			}), true);
		};

		getEnabledTables = function(cluster) {
			var data = [];

			$.each(stateMap.tableNameMap, function(idx, table) {
				if (table.cluster === cluster && table.enabled) {
					data.push({name:table.name, rowCount:table.rows, recordCount:table.records});
				}
			});

			return data;
		};

		getDisabledTables = function(cluster) {
			var data = [];

			$.each(stateMap.tableNameMap, function(idx, table) {
				if (table.cluster === cluster && !table.enabled) {
					data.push({name:table.name, rowCount:table.rows, recordCount:table.records});
				}
			});

			return data;
		};

		isDataLoaded = function() {
			return stateMap.tableNameMap !== null;
		};

		disableTable = function(tableName) {
			configMap.poller.disableTable(tableName);
		};

		enableTable = function(tableName) {
			configMap.poller.enableTable(tableName);
		};

		deleteTable = function(tableName, includeFiles) {
			configMap.poller.deleteTable(tableName, includeFiles);
		};

		getSchema = function(tableName) {
			return configMap.poller.getSchema(tableName);
		};

		findTerms = function(table, family, column, startsWith) {
			configMap.poller.findTerms(table, family, column, startsWith, function(terms) {
				$.gevent.publish('terms-updated', terms);
			});
		};

		return {
			getClusters : getClusters,
			getEnabledTables : getEnabledTables,
			getDisabledTables : getDisabledTables,
			isDataLoaded : isDataLoaded,
			disableTable : disableTable,
			enableTable : enableTable,
			deleteTable : deleteTable,
			getSchema : getSchema,
			findTerms : findTerms
		};
	}());

	nodes = (function() {
		var getOfflineZookeeperNodes, getOfflineControllerNodes, getOfflineShardNodes, isDataLoaded;

		getOfflineZookeeperNodes = function() {
			return stateMap.nodeMap.zookeepers.offline;
		};

		getOfflineControllerNodes = function() {
			return stateMap.nodeMap.controllers.offline;
		};

		getOfflineShardNodes = function(clusterName) {
			var clusterData = $.grep(stateMap.nodeMap.clusters, function(cluster) {
				return cluster.name === clusterName;
			});

			if (clusterData.length > 0) {
				return clusterData[0].offline;
			}
			return [];
		};

		isDataLoaded = function() {
			return stateMap.nodeMap !== null;
		};

		return {
			getOfflineZookeeperNodes : getOfflineZookeeperNodes,
			getOfflineControllerNodes : getOfflineControllerNodes,
			getOfflineShardNodes : getOfflineShardNodes,
			isDataLoaded : isDataLoaded
		};
	}());

	metrics = (function() {
		var getZookeeperChartData, getControllerChartData, getClusters, getShardChartData, getTableChartData,
			getQueryLoadChartData, buildPieChartData, getSlowQueryWarnings;

		getZookeeperChartData = function() {
			return buildPieChartData(stateMap.nodeMap.zookeepers.online.length, stateMap.nodeMap.zookeepers.offline.length);
		};

		getControllerChartData = function() {
			return buildPieChartData(stateMap.nodeMap.controllers.online.length, stateMap.nodeMap.controllers.offline.length);
		};

		getClusters = function() {
			return $.map(stateMap.nodeMap.clusters, function(cluster) {
				return cluster.name;
			});
		};

		getShardChartData = function(clusterName) {
			var clusterData = $.grep(stateMap.nodeMap.clusters, function(cluster) {
				return cluster.name === clusterName;
			});

			if (clusterData.length > 0) {
				return buildPieChartData(clusterData[0].online.length, clusterData[0].offline.length);
			}
			return null;
		};

		getTableChartData = function() {
			var enabledData = blurconsole.utils.reduce(stateMap.tableNameMap, [], function(accumulator, table){
				var currentCluster = $.grep(accumulator, function(item){
					return item[0] === table.cluster;
				});

				if (currentCluster.length === 0) {
					currentCluster = [table.cluster, 0];
					accumulator.push(currentCluster);
				} else {
					currentCluster = currentCluster[0];
				}

				if (table.enabled) {
					currentCluster[1] = currentCluster[1]+1;
				}
				return accumulator;
			});

			var disabledData = blurconsole.utils.reduce(stateMap.tableNameMap, [], function(accumulator, table){
				var currentCluster = $.grep(accumulator, function(item){
					return item[0] === table.cluster;
				});

				if (currentCluster.length === 0) {
					currentCluster = [table.cluster, 0];
					accumulator.push(currentCluster);
				} else {
					currentCluster = currentCluster[0];
				}

				if (!table.enabled) {
					currentCluster[1] = currentCluster[1]+1;
				}
				return accumulator;
			});

			return [
				{
					'data' : enabledData,
					'label' : 'Enabled',
					'color' : '#66CDCC',
					'stack' : true
				},
				{
					'data' : disabledData,
					'label' : 'Disabled',
					'color' : '#333333',
					'stack' : true
				}
			];
		};

		getQueryLoadChartData = function() {
			var total = 0,
				queryArray = [],
				meanArray = [],
				queryData, mean;

			queryData = stateMap.queryPerformance;

			$.each(queryData, function(idx, increment) {
				total += increment;
			});

			mean = queryData.length === 0 ? 0 : total/queryData.length;

			$.each(queryData, function(idx, increment) {
				queryArray.push([idx, increment]);
				meanArray.push([idx, mean]);
			});

			return [queryArray, meanArray];
		};

		buildPieChartData = function(onlineCount, offlineCount) {
			var onlineChart = {
				'label':'Online',
				'color':'#66CDCC',
				'data':[[0,onlineCount]]
			};

			var offlineChart = {
				'label':'Offline',
				'color':'#FF1919',
				'data':[[0,offlineCount]]
			};

			return [onlineChart, offlineChart];
		};

		getSlowQueryWarnings = function() {
			return stateMap.queries.slowQueries;
		};

		return {
			getZookeeperChartData : getZookeeperChartData,
			getControllerChartData : getControllerChartData,
			getClusters : getClusters,
			getShardChartData : getShardChartData,
			getTableChartData : getTableChartData,
			getQueryLoadChartData : getQueryLoadChartData,
			getSlowQueryWarnings : getSlowQueryWarnings
		};
	}());

	queries = (function() {
		var queriesForTable, cancelQuery, tableHasActivity, matchesFilter,
			states = ['running', 'interrupted', 'complete', 'backpressureinterrupted'];

		queriesForTable = function(table, sort, filter) {
			var queries = [], qSort, sortField, sortDir;

			qSort = (sort || 'startTime~desc').split('~');
			sortField = qSort[0];
			sortDir = qSort.length > 1 ? qSort[1] : 'asc';

			$.each(stateMap.queries.queries, function(i, query){
				if (query.table === table && matchesFilter(query, filter)) {
					queries.push(query);
				}
			});

			queries.sort(function(a, b){
				if (sortDir === 'asc') {
					return a[sortField] > b[sortField];
				} else {
					return b[sortField] > b[sortField];
				}
			});

			return queries;
		};

		cancelQuery = function(uuid) {
			configMap.poller.cancelQuery(uuid);
		};

		tableHasActivity = function(table) {
			var hasActivity = false;
			$.each(stateMap.queries.queries, function(i, query){
				if (query.table === table) {
					hasActivity = true;
					return false;
				}
			});
			return hasActivity;
		};

		matchesFilter = function(queryData, filterText) {
			var queryStr = queryData.user + '~~~' + queryData.query + '~~~' + states[queryData.state];

			if (filterText === null || filterText === '') {
				return true;
			}

			return queryStr.toLowerCase().indexOf(filterText.toLowerCase()) !== -1;
		};

		return {
			queriesForTable : queriesForTable,
			cancelQuery : cancelQuery,
			tableHasActivity : tableHasActivity
		};
	}());

	nodePoller = function() {
		var tmpNodeMap = configMap.poller.getNodeList();
		if (!blurconsole.utils.equals(tmpNodeMap, stateMap.nodeMap)) {
			stateMap.nodeMap = tmpNodeMap;
			$.gevent.publish('node-status-updated');
		}
		setTimeout(nodePoller, 5000);
	};

	tablePoller = function() {
		var tmpTableMap = configMap.poller.getTableList();
		if (!blurconsole.utils.equals(tmpTableMap, stateMap.tableNameMap)) {
			stateMap.tableNameMap = tmpTableMap;
			$.gevent.publish('tables-updated');
		}
		setTimeout(tablePoller, 5000);
	};

	queryPerformancePoller = function() {
		if (stateMap.queryPerformance.length === 100) {
			stateMap.queryPerformance.shift();
		}

		stateMap.queryPerformance.push(configMap.poller.getQueryPerformance());
		$.gevent.publish('query-perf-updated');
		setTimeout(queryPerformancePoller, 5000);
	};

	queryPoller = function() {
		var tmpQueries = configMap.poller.getQueries();
		if (!blurconsole.utils.equals(tmpQueries, stateMap.queries)) {
			stateMap.queries = tmpQueries;
			$.gevent.publish('queries-updated');
		}
		setTimeout(queryPoller, 5000);
	};

	initModule = function() {
		configMap.poller = isFakeData ? blurconsole.fake : blurconsole.data;
		setTimeout(function() {
			nodePoller();
			tablePoller();
			queryPerformancePoller();
			queryPoller();
		}, 1000);
	};
	return {
		initModule : initModule,
		tables : tables,
		metrics: metrics,
		nodes : nodes,
		queries : queries
	};
}());