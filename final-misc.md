# w251:Fall 2018 - Final Project: Random Infrastructure Snippets

#### Homework Assignment - Part 1 Cluster Setup

#### Refrenced Articles

https://docs.mongodb.com/manual/tutorial/deploy-sharded-cluster-hashed-sharding/#deploy-hashed-sharded-cluster-shard-database
https://askubuntu.com/questions/921753/failed-to-start-mongod-service-unit-mongod-service-not-found
https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/
https://docs.mongodb.com/manual/tutorial/remove-replica-set-member/
https://www.anintegratedworld.com/uninstall-mongodb-in-ubuntu-via-command-line-in-3-easy-steps/
https://www.bmc.com/blogs/mongodb-sharding-explained/
https://www.digitalocean.com/community/tutorials/how-to-set-up-a-firewall-with-ufw-on-ubuntu-14-04
https://www.digitalocean.com/community/tutorials/how-to-install-and-secure-mongodb-on-ubuntu-16-04#part-two-securing-mongodb
https://docs.mongodb.com/manual/sharding/
https://docs.mongodb.com/manual/core/hashed-sharding/
https://docs.mongodb.com/manual/core/hashed-sharding/#hashed-sharding-shard-key
https://docs.mongodb.com/manual/tutorial/expand-replica-set/
https://github.com/yougov/mongo-connector/wiki/Configuration-Options#docmanagersindexargs
https://medium.com/@AzilenTech/integrating-elastic-search-with-mongodb-using-no-sql-6e67a8172a5a

#### Machines 

```
:..........:...............:................:...............:............:.....................:
:    id    :    hostname   :   primary_ip   :   backend_ip  : datacenter :        action       :
:..........:...............:................:...............:............:.....................:
: 65640583 : elasticsearch : 169.53.133.183 : 10.122.165.17 :   sjc01    :          -          :
: 65277737 :     mongo1    : 169.53.133.179 :  10.122.165.5 :   sjc01    :          -          :
: 65280581 :     mongo2    : 169.53.133.188 : 10.122.165.15 :   sjc01    :          -          :
: 65870741 :     mongo3    : 169.53.133.187 : 10.122.165.19 :   sjc01    :          -          :
: 65876637 :     mongo4    : 169.53.133.190 : 10.122.165.20 :   sjc01    :          -          :
: 65876793 :     mongo5    : 169.53.133.180 : 10.122.165.29 :   sjc01    : Publish server data :
: 64528261 :     spark1    : 169.62.86.178  :   10.87.60.9  :   sjc04    :          -          :
:..........:...............:................:...............:............:.....................:
```


#### Mongo Shard Setup

* shards on 17022
* config on 17042
* mongos on 32


Standing up / initializing configuration replica set
```
rs.initiate()
{
	"info2" : "no configuration specified. Using a default configuration for the set",
	"me" : "169.53.133.179:27022",
	"ok" : 1,
	"$gleStats" : {
		"lastOpTime" : Timestamp(1542497162, 1),
		"electionId" : ObjectId("000000000000000000000000")
	},
	"lastCommittedOpTime" : Timestamp(0, 0)
}

rs.add( { host: "169.53.133.187:27042", priority: 0, votes: 0 } )
rs.add( { host: "169.53.133.190:27042", priority: 0, votes: 0 } )
rs.add( { host: "169.53.133.180:27042", priority: 0, votes: 0 } )

```

Each config daemon on the replica set was hosted as service running mongod using the following 

```
Config Server
sharding:
  configDB: cfg-stock-shard-2/169.53.133.188:27042
net:
  bindIp: localhost,169.53.133.179:27032
```


#### Shard Cluster Setup


ReplicaSet Setup
```
rs.initiate(
  {
    _id: "stock-tweets",
    configsvr: true,
    members: [
      { _id : 0, host : "169.53.133.179:27022" },
      { _id : 1, host : "169.53.133.188:27022" }
    ]
  }
)

```

Shard Setup

```

sh.addShard( "stock-tweet-2/169.53.133.188:27022")
sh.addShard( "stock-tweet-3/169.53.133.187:27022")
sh.addShard( "stock-tweet-4/169.53.133.190:27022")
sh.addShard( "stock-tweet-5/169.53.133.180:27022")


sh.enableSharding("qualitative_stock_db")
{
	"ok" : 1,
	"operationTime" : Timestamp(1542514266, 5),
	"$clusterTime" : {
		"clusterTime" : Timestamp(1542514266, 5),
		"signature" : {
			"hash" : BinData(0,"AAAAAAAAAAAAAAAAAAAAAAAAAAA="),
			"keyId" : NumberLong(0)
		}
	}
}


sh.enableSharding("quantitative_stock_db")
sh.shardCollection("quantitative_stock_db.ticker_scraps", { ticker_scrap_id : "hashed" } )
sh.shardCollection("qualitative_stock_db_2.tweets", { tweet_id : "hashed" } )

```

Mongos Router Setup

```


mongod --config /data/config/mongo-shard.conf

sharding:
  configDB: cfg-stock-tweets/169.53.133.179:27042
net:
  port: 27032
  bindIp: localhost,169.53.133.179

```

Mongod Daemon 
* Though mongos router was the entry point, each box had a standard mongod daemon running

```


# mongod.conf

# for documentation of all options, see:
#   http://docs.mongodb.org/manual/reference/configuration-options/

# Where and how to store data.
storage:
  dbPath: /data
  journal:
    enabled: true
#  engine:
#  mmapv1:
#  wiredTiger:

# where to write logging data.
systemLog:
  destination: file
  logAppend: true
  path: /var/log/mongodb/mongod.log

# network interfaces
net:
  port: 27042
  bindIp: localhost,169.53.133.190


# how the process runs
processManagement:
  timeZoneInfo: /usr/share/zoneinfo

#security:

#operationProfiling:

replication:
  replSetName: cfg-stock-shard-2

sharding:
  clusterRole: configsvr

## Enterprise-Only Options:

#auditLog:

#snmp:




```

Random  statements used against mongos

```

db.tweets.createIndex({'date':1})
db.createCollection("ticker_scraps")
db.ticker_scraps.createIndex({'date':1, 'time':1, })



# for some conversions

use qual_conv_stock_db;
db.tweets.find({Sentiment: {$exists: true}}).forEach(function(obj) { 
    obj.Sentiment = new NumberInt(obj.Sentiment);
    db.tweets.save(obj);
});

# for some record moving

use qualitative_stock_db;
var docs=db.tweets.find();
use qual_conv_stock_db;
docs.forEach(function(doc) { db.tweets.insert(doc); });



db.tweets.find({Sentiment: {$exists: true}}).forEach(function(obj) { 
    obj.Sentiment = new NumberInt(obj.Sentiment);
    db.tweets_bak.save(obj);
});


```


Final DB Setup
```
TWEETS
db-name
qualitative_stock_db
collection
tweets

index on column - date
hashing on column - tweet_id



TICKER INFO
db-name
quantitative_stock_db
collection
ticker_scraps

index on column - date
hashing on column - ticker_scrap_id

```


#### Mongo-Connector


Execution Statement - Ran using tmux
```
mongo-connector -m 169.53.133.179:27032 -t 169.53.133.183:9200 -d elastic_doc_manager
```

Some logs


```
2018-12-16 04:18:50,201 [ALWAYS] mongo_connector.connector:50 - Starting mongo-connector version: 3.1.0
2018-12-16 04:18:50,201 [ALWAYS] mongo_connector.connector:50 - Python version: 3.6.7 (default, Oct 22 2018, 11:32:17)
[GCC 8.2.0]
2018-12-16 04:18:50,202 [ALWAYS] mongo_connector.connector:50 - Platform: Linux-4.15.0-32-generic-x86_64-with-Ubuntu-18.04-bionic
2018-12-16 04:18:50,202 [ALWAYS] mongo_connector.connector:50 - pymongo version: 3.7.2
2018-12-16 04:18:50,202 [WARNING] mongo_connector.connector:170 - MongoConnector: Can't find /root/oplog.timestamp, attempting to create an empty progress log
2018-12-16 04:18:50,210 [ALWAYS] mongo_connector.connector:50 - Source MongoDB version: 4.0.4
2018-12-16 04:18:50,211 [ALWAYS] mongo_connector.connector:50 - Target DocManager: mongo_connector.doc_managers.elastic2_doc_manager version: 1.0.0
```

Checksum Values used to signal change for the mongo-connector to move data

```
[["stock-tweet-5", 6636101433009636045], ["stock-tweet-2", 6636237385904422914], ["stock-tweet-4", 6636101433009636044], ["stock-tweet-3", 6636101433009636049]]
```







#### ElasticSearch Setup


Some random commands used during setup / trouble shooting

```
curl -XDELETE http://169.53.133.184:9200/mongodb_meta
curl -XDELETE http://169.53.133.184:9200/quantitative_stock_db
curl -XDELETE http://169.53.133.184:9200/testdb
curl -XDELETE http://169.53.133.184:9200/qualitative_stock_db
curl -XDELETE "http://169.53.133.184:9200/qual_conv_stock_db'
curl -XPUT http://169.53.133.184:9200/qualitative_stock_db
curl -XPOST http://169.53.133.184:9200/qualitative_stock_db/_refresh
```

ElasticSearch Configuration 

```
# ======================== Elasticsearch Configuration =========================
#
# NOTE: Elasticsearch comes with reasonable defaults for most settings.
#       Before you set out to tweak and tune the configuration, make sure you
#       understand what are you trying to accomplish and the consequences.
#
# The primary way of configuring a node is via this file. This template lists
# the most important settings you may want to configure for a production cluster.
#
# Please consult the documentation for further information on configuration options:
# https://www.elastic.co/guide/en/elasticsearch/reference/index.html
#
# ---------------------------------- Cluster -----------------------------------
#
# Use a descriptive name for your cluster:
#
#cluster.name: my-application
#
# ------------------------------------ Node ------------------------------------
#
# Use a descriptive name for the node:
#
#node.name: node-1
#
# Add custom attributes to the node:
#
#node.attr.rack: r1
#
# ----------------------------------- Paths ------------------------------------
#
# Path to directory where to store the data (separate multiple locations by comma):
#
path.data: /data/elasticsearch
#
# Path to log files:
#
#path.logs: /path/to/logs
#
# ----------------------------------- Memory -----------------------------------
#
# Lock the memory on startup:
#
#bootstrap.memory_lock: true
#
# Make sure that the heap size is set to about half the memory available
# on the system and that the owner of the process is allowed to use this
# limit.
#
# Elasticsearch performs poorly when the system is swapping the memory.
#
# ---------------------------------- Network -----------------------------------
#
# Set the bind address to a specific IP (IPv4 or IPv6):
#
network.host: 169.53.133.184
#
# Set a custom port for HTTP:
#
http.port: 9200
#
# For more information, consult the network module documentation.
#
# --------------------------------- Discovery ----------------------------------
#
# Pass an initial list of hosts to perform discovery when new node is started:
# The default list of hosts is ["127.0.0.1", "[::1]"]
#
#discovery.zen.ping.unicast.hosts: ["host1", "host2"]
#
# Prevent the "split brain" by configuring the majority of nodes (total number of master-eligible nodes / 2 + 1):
#
#discovery.zen.minimum_master_nodes: 3
#
# For more information, consult the zen discovery module documentation.
#
# ---------------------------------- Gateway -----------------------------------
#
# Block initial recovery after a full cluster restart until N nodes are started:
#
#gateway.recover_after_nodes: 3
#
# For more information, consult the gateway module documentation.
#
# ---------------------------------- Various -----------------------------------
#
# Require explicit names when deleting indices:
#
#action.destructive_requires_name: true

```


#### Kibana Setup

Yaml File

```


# Kibana is served by a back end server. This setting specifies the port to use.
server.port: 5601

# Specifies the address to which the Kibana server will bind. IP addresses and host names are both valid values.
# The default is 'localhost', which usually means remote machines will not be able to connect.
# To allow connections from remote users, set this parameter to a non-loopback address.
server.host: "169.53.133.181"

# Enables you to specify a path to mount Kibana at if you are running behind a proxy. This only affects
# the URLs generated by Kibana, your proxy is expected to remove the basePath value before forwarding requests
# to Kibana. This setting cannot end in a slash.
#server.basePath: ""

# The maximum payload size in bytes for incoming server requests.
#server.maxPayloadBytes: 1048576

# The Kibana server's name.  This is used for display purposes.
#server.name: "your-hostname"

# The URL of the Elasticsearch instance to use for all your queries.
elasticsearch.url: "http://169.53.133.184:9200"

# When this setting's value is true Kibana uses the hostname specified in the server.host
# setting. When the value of this setting is false, Kibana uses the hostname of the host
# that connects to this Kibana instance.
#elasticsearch.preserveHost: true

# Kibana uses an index in Elasticsearch to store saved searches, visualizations and
# dashboards. Kibana creates a new index if the index doesn't already exist.
#kibana.index: ".kibana"

# The default application to load.
#kibana.defaultAppId: "discover"

# If your Elasticsearch is protected with basic authentication, these settings provide
# the username and password that the Kibana server uses to perform maintenance on the Kibana
# index at startup. Your Kibana users still need to authenticate with Elasticsearch, which
# is proxied through the Kibana server.
#elasticsearch.username: "user"
#elasticsearch.password: "pass"

# Enables SSL and paths to the PEM-format SSL certificate and SSL key files, respectively.
# These settings enable SSL for outgoing requests from the Kibana server to the browser.
#server.ssl.enabled: false
#server.ssl.certificate: /path/to/your/server.crt
#server.ssl.key: /path/to/your/server.key

# Optional settings that provide the paths to the PEM-format SSL certificate and key files.
# These files validate that your Elasticsearch backend uses the same key files.
#elasticsearch.ssl.certificate: /path/to/your/client.crt
#elasticsearch.ssl.key: /path/to/your/client.key

# Optional setting that enables you to specify a path to the PEM file for the certificate
# authority for your Elasticsearch instance.
#elasticsearch.ssl.certificateAuthorities: [ "/path/to/your/CA.pem" ]

# To disregard the validity of SSL certificates, change this setting's value to 'none'.
#elasticsearch.ssl.verificationMode: full

# Time in milliseconds to wait for Elasticsearch to respond to pings. Defaults to the value of
# the elasticsearch.requestTimeout setting.
#elasticsearch.pingTimeout: 1500

# Time in milliseconds to wait for responses from the back end or Elasticsearch. This value
# must be a positive integer.
#elasticsearch.requestTimeout: 30000

# List of Kibana client-side headers to send to Elasticsearch. To send *no* client-side
# headers, set this value to [] (an empty list).
#elasticsearch.requestHeadersWhitelist: [ authorization ]

# Header names and values that are sent to Elasticsearch. Any custom headers cannot be overwritten
# by client-side headers, regardless of the elasticsearch.requestHeadersWhitelist configuration.
#elasticsearch.customHeaders: {}

# Time in milliseconds for Elasticsearch to wait for responses from shards. Set to 0 to disable.
#elasticsearch.shardTimeout: 0

# Time in milliseconds to wait for Elasticsearch at Kibana startup before retrying.
#elasticsearch.startupTimeout: 5000

# Specifies the path where Kibana creates the process ID file.
#pid.file: /var/run/kibana.pid

# Enables you specify a file where Kibana stores log output.
#logging.dest: stdout

# Set the value of this setting to true to suppress all logging output.
#logging.silent: false

# Set the value of this setting to true to suppress all logging output other than error messages.
#logging.quiet: false

# Set the value of this setting to true to log all events, including system usage information
# and all requests.
#logging.verbose: false

# Set the interval in milliseconds to sample system and process performance
# metrics. Minimum is 100ms. Defaults to 5000.
#ops.interval: 5000

# The default locale. This locale can be used in certain circumstances to substitute any missing
# translations.
#i18n.defaultLocale: "en"


```

