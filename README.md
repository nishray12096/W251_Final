# W251 Final - collecting tweets and sentiments and applying the data for analysis and stock price prediction
# Nishant Velagapudi, Kirby Bloom, Brandon Cummings, Ravi Ayyappan

Contains files for our w251 project - a framework for twitter sentiment with a case study in stock fluctuation prediction.

W251_01_Bloom_Cummings_Ayyappan_Velagapudi_Slides.pdf - contains the presented slides
W251_01_Bloom_Cummings_Ayyappan_Velagapudi_WhitePaper.pdf - contains the writeup  

TrainSentiment_RNN.py - contains code to read from Mongo, train an RNN, evaluate, and output basic RMSE for the test set

pophashtag.scala - contains code to process the Twitter stream and write it into MongoDB  

parseAndStoreStockPrices.scala - contains code to get minute by minute stock data and write it into MongoDB  

SentimentUtils.scala - contains utility function to derive the sentiment for a given tweet  




### final-misc

File includes some commands and configuration files used to stand up the infastructure for project.

This includes
* Mongo
* Mongo Shard Cluster
* Mongo-connector
* Elastic Search
* Kibana
