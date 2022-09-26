# vert.xAPI - some examples onhow future works

to get result from getRegisteredQuery

Future<String> query = getRegisteredQuery()
if query.setHandler(handler ->{ 
if(handler.succeeded()) {
            		 query.complete(rc.result());
            	 }else 
            	 {
            		 query.fail(rc.cause());
            	 }
               
               )

public Future<String> getRegisteredQuery(WebClient webClient,WebClient webClientredis, String queryId) {
    	Future<String> future = Future.future();
        String query = cache.getIfPresent(queryId);
        logger.debug("Query Id {} Query: {}", queryId, query);
        if (query != null) {
            
            logger.info("query found in local cache");
            future.complete(query);
            return future;

        } else {
             callCdmRedisApi(webClient,webClientredis, queryId).setHandler(rc->{
            	 if(rc.succeeded()) {
            		 future.complete(rc.result());
            	 }else 
            	 {
            		 future.fail(rc.cause());
            	 }
             }); 
             
             return future ; 
             
             }
      }
      
      
      /***
     * @author hhebburs
     * @return  Future that will have a response either from Redis first if not CDM 
     * @param webClient - cdm web client
     * @param webClientredis - redis web client
     * @param queryId - QueryId of the query that needs to be retrieved from redis first if not cdm
     * fn name - callCdmRedisApi
     * The function get registered query gets the query from either cdm or redis and stores it in the local cache
     * ( Cdm is called only if redis fails ) 
     * This function gets called only if query is not available in local cache
     */
    
    private Future<String> callCdmRedisApi(WebClient webClient,WebClient webClientredis, String queryId) {
    	
        Future<String> future = Future.future();
        Future<String> futureRedis = Future.future();
        
        String redisGetURL = config.getString(QsDpConstants.get_url)+"/"+queryId ;
        String redisPutURL = config.getString(QsDpConstants.put_url)+"/"+queryId ;

        logger.debug("Redis endpoint to for getting the cache information : " + redisGetURL );
        
        webClientredis.getAbs(redisGetURL)
        .putHeader(QsDpConstants.requestedBy, MDC.get(QsDpConstants.correlation_id))
        .putHeader(QsDpConstants.Content_Type, QsDpConstants.app_json)
        .send(ar -> {
             
        	if (ar.succeeded() && ar.result().statusCode() == 200 ) {           	
                @Nullable
				String bodyAsString = ar.result().bodyAsString();
                logger.info("Received response from redis " + ar.result().statusCode());
                cache.put(queryId, bodyAsString);
                futureRedis.complete(bodyAsString);
                future.complete(bodyAsString);
              } else {
            	if ( !ar.succeeded())
          		{logger.info("Error connecting to redis is : "+ ar.cause().getMessage());}
          		else
          		{logger.info("query not found in cache : "+ ar.result().statusMessage() + " : " + ar.result().statusCode()) ;} 
            	
            	futureRedis.complete(QsDpConstants.RedisFail);
              }
            });
  

        
        String payload = config.getString(QsDpConstants.request_temp)
                .replace(QsDpConstants.ID, queryId);
        String cdmURL = config.getString(QsDpConstants.cdmUrl);
        logger.debug("CDM URL for registered query : {}", cdmURL);
        logger.debug("payload for CDM get registered query : {}", payload);
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        
        /***
         * call Cdm only if redis call fails
         * 
         */
        futureRedis.setHandler(fris ->{ 
        	
        	
        	if (fris.succeeded()) {
        		logger.debug("fris.result() is " + fris.result());
        		if (fris.result() == QsDpConstants.RedisFail) {
				        		webClient.postAbs(cdmURL)
				                .putHeader(QsDpConstants.Content_Type, QsDpConstants.app_json)
				                .sendJsonObject(new JsonObject(payload),
				                        ar -> {
				                            MDC.setContextMap(copyOfContextMap);
				                            if (ar.succeeded()) {
				                                HttpResponse<Buffer> httpResponse = ar.result();
				                                int statusCode = httpResponse.statusCode();
				                                if (statusCode == 200) {
				                                    @Nullable JsonObject body = httpResponse.bodyAsJsonObject();
				                                    parseCdmResult(future, queryId, body ,webClientredis,redisPutURL);
				                                } else {
				                                    logger.error("Query retrieval from cdm failed with status : {} ", statusCode);
				                                    logger.error(httpResponse.bodyAsJsonObject().toString());
				                                    future.fail(new QueryExecutionException("Query retrieval failed").getMessage());
				                                }
				                            } else {
				                                logger.error("Query retrieval failed, unable to connect. ", ar.cause());
				                                future.fail(ar.cause());
				                            }
				                        });
        		}             
               
        		
        	}
        
        
        }); 
        return future ;   
    }
