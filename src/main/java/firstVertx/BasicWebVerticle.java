package firstVertx;


import java.util.ArrayList;
import java.util.List;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import models.Customer;

public class BasicWebVerticle extends AbstractVerticle{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BasicWebVerticle.class) ; 
	
	public static List<Customer> customerList = new ArrayList<Customer>() ; 
	
	static{ 
		customerList.add(new Customer(1, "hemanth")) ;
		customerList.add(new Customer(2, "Sukreet")) ; 
		customerList.add(new Customer(3, "Vinu")) ; 	
	}

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx() ; 
		
		
		ConfigRetriever configRetriever = ConfigRetriever.create(vertx);

        configRetriever.getConfig(config -> {
        	
       	if (config.succeeded()) {
        		
        		JsonObject configJson = config.result();
        		//System.out.println(configJson.encodePrettily());
        		DeploymentOptions options = new DeploymentOptions().setConfig(configJson);
            	vertx.deployVerticle(new BasicWebVerticle(), options);

       		}
       	
       	
        });
		

	}

	@Override
	public void start() throws Exception {
		LOGGER.info("Verticle basic Verticle started");
		Router router = Router.router(vertx);
		router.route("/api*").handler(this::DefaultHandlerAllEndPts);
		router.route("/api/v1/products*").handler(BodyHandler.create()); 
		router.get("/api/v1/products").handler(this::getAllProducts) ; 
		router.get("/api/v1/products/:id").handler(this::getProductById) ;
		router.post("/api/v1/products").handler(this::addProduct) ;
		router.delete("/api/v1/products/:id").handler(this::deleteProduct) ;
		router.route().handler(StaticHandler.create().setCachingEnabled(false));
		vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port"), asycResult -> { 
			if ( asycResult.succeeded()){ 
				
				LOGGER.info("connection succeded . Listning to port : "+ config().getInteger("http.port").toString() );
				
			}else{ 
				
				LOGGER.error("Could not start the Http server : " + asycResult.cause() );
			}
		}
					
				);
	}	

	@Override
	public void stop() throws Exception {
		LOGGER.info("Verticle basic Verticle Stopped");
	}
	
	private void getAllProducts(RoutingContext routingContext) {
		JsonObject responseJson = new JsonObject();
		responseJson.put("Customers", customerList) ; 
		System.out.println("response should be" +  responseJson.toString()) ; 
		routingContext.response().setStatusCode(200).putHeader("content-type", "application/json")
		.end(Json.encodePrettily(responseJson)) ; 	
	}
	
	private void getProductById(RoutingContext routingContext) {
		Integer id = Integer.valueOf(routingContext.request().getParam("id")) ; 
		System.out.println("id: "+ id  ) ; 
		JsonObject responseJson = new JsonObject();
		Customer c = null; 
		for ( Customer customer : customerList ){ 
			if (customer.getId()==id){ 
				c = customer ; 
				break ; 
			}
		}
		responseJson = responseJson.mapFrom(c);
		System.out.println("Response is" + responseJson.toString()) ; 
		routingContext.response().setStatusCode(200).putHeader("content-type", "application/json")
		.end(Json.encodePrettily(responseJson)) ; 
	}
	
	private void addProduct(RoutingContext routingContext){ 
		
		JsonObject jobj = routingContext.getBodyAsJson() ; 
		Customer c1 = jobj.mapTo(Customer.class) ; 
		int  id = 0 ; 
		for(Customer c : customerList ){ 
			if (c.getId() > id ){ 
				id = c.getId() ; 
			}
		}
		id = id +1 ; 
		c1.setId(id);
		customerList.add(c1); 
		routingContext.response().setStatusCode(201).putHeader("content-type", "application/json")
		.end(Json.encodePrettily(c1)) ; 
	}
	
	private void deleteProduct(RoutingContext routingContext){ 
		int id = Integer.valueOf(routingContext.request().getParam("id")); 
		int i = 0  ; 
		Customer deltedCust = null ; 
		for(Customer c : customerList){ 
			 
			if (id == c.getId()){ 
				
				deltedCust = customerList.get(i) ; 
				customerList.remove(i); 
				break ; 
				
			}
			i = i+1 ;
			
		}
		routingContext.response().setStatusCode(200).putHeader("content-type", "application/json")
		.end(Json.encodePrettily(deltedCust)) ;
		
	}
	
	private void DefaultHandlerAllEndPts(RoutingContext routingContext){ 
		
		String authToken = routingContext.request().headers().get("AuthToken") ;
		Cookie cookie = routingContext.getCookie("name") ; 
		if (cookie ==null ){ 
			cookie = Cookie.cookie("name", "Hemanth") ; 
			cookie.setPath("/");
			cookie.setMaxAge(365*24*60*60) ;
			routingContext.addCookie(cookie) ; 
			
		}
		
		if (!authToken.equals("123") ){ 
			routingContext.response().setStatusCode(400).putHeader("content-type", "application/json").end(Json.encodePrettily(new JsonObject().put("error", "unauthorized"))); ; 
		}else 
		{
			routingContext.next(); 
		}
	}
}
