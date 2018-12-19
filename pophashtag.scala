//import com.typesafe.config.ConfigFactory
import org.apache.log4j.{Level, Logger}
import java.io.File
import org.apache.spark.SparkConf
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
//import org.elasticsearch.spark.rdd.EsSpark
import org.json4s.JsonDSL._

import java.text.SimpleDateFormat
import org.joda.time.{DateTime, Days}
//import StreamingLogger._
import org.apache.spark._
import org.apache.spark.sql.SQLContext._
//import org.apache.spark.sql.SQLConf
import org.apache.spark.sql.SQLContext

//import com.google.gson.Gson

import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.{read, write}
import org.json4s.jackson.Serialization

 import org.json4s.JsonWriter
 import org.json4s.DefaultFormats
// import org.json.JSONWriter
 import org.json4s.ToJsonWritable
 import org.apache.hadoop.yarn.webapp.ToJSON
 import org.apache.spark.api.java.StorageLevels

//mongodb related imports
import com.mongodb.spark._
import org.bson.Document
import java.util.Calendar

import w266.SentimentAnalysis.SentimentUtils._

/**
 *
 * Sends relevant key-value pairs from the Tweets
 
 *
 */
object TwitterHashTagStream extends App {
  
   // Set logging level if log4j not configured (override by adding log4j.properties to classpath)
    Logger.getLogger("org").setLevel(Level.ERROR)
    Logger.getLogger("akka").setLevel(Level.ERROR)
    Logger.getRootLogger.setLevel(Level.ERROR)


    //only words function
    def onlyWords(text: String) : String = {
		text.split(" ").filter(_.matches("^[a-zA-Z0-9 ]+$")).fold("")((a,b) => a + " " + b).trim
    }


    //val filters : Array[String] = Array("stock","stocks","stockmarket","investor","trading")
    val filters : Array[String] = Array("NYSE","NASDAQ","DOW","SP500","USD","WarrenBuffett","IPO","Apple","AAPL","Tesla","Tsla","Facebook","nflx","fb","goog","amzn","qqq","economy","personalfinance","401k","retirement","millennial","debt","Fed","GDP","investing","daytrading","business","finance","equity","wallstreet","trader","financialnews","financialadvice","largecap","smallcap","microcap","OPEC","gold","china","commodities","oil","advisors","wealthmanagement","stockmarket","money","forex","stocks","forextrader","binaryoptions","bitcoin","entrepreneur","trading","investor","forextrading","profit","daytrader","forexsignals","invest", "millionaire" ,"cryptocurrency","investment", "wealth", "stock")

    // Initialize a SparkConf with all available cores
    val sparkConf = new SparkConf().setAppName("HashTagStreamProj")

    // check Spark configuration for master URL, set it to local if not configured
    if (!sparkConf.contains("spark.master")) {
      sparkConf.setMaster("local[2]")
    }
    
    //sparkConf.set("spark.mongodb.input.uri", "mongodb://169.53.133.179:27032/testdb.tweets")
    //sparkConf.set("spark.mongodb.output.uri", "mongodb://169.53.133.179:27032/testdb.tweets")

    sparkConf.set("spark.mongodb.input.uri", "mongodb://169.53.133.179:27032/qualitative_stock_db.tweets")
    sparkConf.set("spark.mongodb.output.uri", "mongodb://169.53.133.179:27032/qualitative_stock_db.tweets")

    // Create a StreamingContext with a batch interval of 2 seconds.
    val ssc = new StreamingContext(sparkConf, Seconds(2))
    
   // Create a DStream the gets streaming data from Twitter with the filters provided
    val stream = TwitterUtils.createStream(ssc, None, filters,StorageLevels.MEMORY_AND_DISK).filter{ x => x.getLang == "en"}
    
    // Process each tweet in a batch
    val tweetMap = stream.map(status => {

      // Defined a DateFormat to convert date Time provided by Twitter to a format understandable

    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

    def checkObj (x : Any) : String  = {
        x match {
          case i:String => i
          case _ => ""
        }
      }
    
   
      // Creating a JSON using json4s.JSONDSL with fields from the tweet and calculated fields
        val tweetMap =
          ("UserID" -> status.getUser.getId) ~
          ("tweet_id" -> status.getId) ~
          ("date" -> formatter.format(Calendar.getInstance.getTime)) ~
          ("UserDescription" -> status.getUser.getDescription) ~
          ("UserScreenName" -> status.getUser.getScreenName) ~
          ("UserFriendsCount" -> status.getUser.getFriendsCount) ~
          ("UserFavouritesCount" -> status.getUser.getFavouritesCount) ~
          ("UserFollowersCount" -> status.getUser.getFollowersCount) ~
          // Ratio is calculated as the number of followers divided by number of people followed
          ("UserFollowersRatio" -> status.getUser.getFollowersCount.toFloat / status.getUser.getFriendsCount.toFloat) ~ {
          if (status.getGeoLocation != null) {
            ("GeoLatitude" -> status.getGeoLocation.getLatitude.toDouble) ~
            ("GeoLongitude" -> status.getGeoLocation.getLongitude.toDouble) 
          }
          else 
              ("GeoLatitude" -> "") ~ ("GeoLongitude" -> "")
          } ~
          ("UserLang" -> status.getUser.getLang) ~
          ("UserLocation" -> status.getUser.getLocation) ~
          ("UserVerification" -> status.getUser.isVerified) ~
          ("UserName" -> status.getUser.getName) ~
          ("UserStatusCount" -> status.getUser.getStatusesCount) ~
          // User Created DateTime is first converted to epoch miliseconds and then converted to the DateFormat defined above
          ("UserCreated" -> formatter.format(status.getUser.getCreatedAt.getTime)) ~
          ("Text" -> status.getText.toString()) ~
          ("TextLength" -> status.getText.length) ~
          //("PlaceName" -> (status.getPlace.getName.toString()))~
          //("PlaceCountry" -> (status.getPlace.getCountry.toString()))~
          //Tokenized the tweet message and then filtered only words starting with #
          ("HashTags" -> status.getText.split(" ").filter(_.startsWith("#")).mkString(" ")) ~
          ("StatusCreatedAt" -> formatter.format(status.getCreatedAt.getTime))  ~
	  ("Sentiment" -> detectSentiment(status.getText).toString)
          

      // This function takes Map of tweet data and returns true if the message is not a spam
      def spamDetector(tweet: Map[String, Any]): Boolean = {
        {
          // Remove recently created users = Remove Twitter users who's profile was created less than a day ago
          Days.daysBetween(new DateTime(formatter.parse(tweet.get("UserCreated").mkString).getTime),
            DateTime.now).getDays > 1
        } & {
          // Users That Create Little Content =  Remove users who have only ever created less than 50 tweets
          tweet.get("UserStatusCount").mkString.toInt > 50
        } & {
          // Remove Users With Few Followers
          tweet.get("UserFollowersRatio").mkString.toFloat > 0.01
        } & {
          // Remove Users With Short Descriptions
          tweet.get("UserDescription").mkString.length > 20
        } & {
          // Remove messages with a Large Numbers Of HashTags
          tweet.get("Text").mkString.split(" ").filter(_.startsWith("#")).length < 5
        } & {
          // Remove Messages with Short Content Length
          tweet.get("TextLength").mkString.toInt > 20
        } & {
          // Remove Messages Requesting Retweets & Follows
          val filters = List("rt and follow", "rt & follow", "rt+follow", "follow and rt", "follow & rt", "follow+rt")
          !filters.exists(tweet.get("Text").mkString.toLowerCase.contains)
        } 
      }
      // If the tweet passed through all the tests in SpamDetector Spam indicator = FALSE else TRUE
      spamDetector(tweetMap.values) match {
        case true => tweetMap.values.+("Spam" -> false)
        case _ => tweetMap.values.+("Spam" -> true)
      }
     val render_json = compact(render(tweetMap))
     render_json
    })
 
    //val tweetMap2 = stream.map(status => {
      
    //   val myjson =   ("UserID" -> "Ravi")~ ("UserDescription" -> "this is a test description") ~  ("UserScreenName" -> "ravi_screen_name") 
    //   val render_json = compact(render(myjson))
    //   render_json
    //})

    // Keep count of how many Tweets we've received so we can stop automatically
    // (and not fill up your disk!)
    var totalTweets:Long = 0

    val tweetMapjson = tweetMap.map(tw => Document.parse(tw)) 
        
    tweetMapjson.foreachRDD((rdd, time) => {
      // Don't bother with empty batches
      if (rdd.count() > 0) {
        // Combine each partition's results into a single RDD:
        val repartitionedRDD = rdd.repartition(1).cache()
        // And print out a directory with the results.
        try {
            //repartitionedRDD.saveAsTextFile("Twitter_" + time.milliseconds.toString)
            repartitionedRDD.saveToMongoDB()
        } catch {
          case sparkError: SparkException if sparkError.getCause() != null => 
             val cause = sparkError.getCause()
             println(s"!!! Failed to save RDD, as expected, here is an error ${cause.getClass.getName}: ${cause.getMessage}")
        }
        // Stop once we've collected 1000 tweets.
        totalTweets += repartitionedRDD.count()
        println("Tweet count: " + totalTweets)
      }
    })
    
  
    ssc.start  // Start the computation
    ssc.awaitTermination  // Wait for the computation to terminate
}
