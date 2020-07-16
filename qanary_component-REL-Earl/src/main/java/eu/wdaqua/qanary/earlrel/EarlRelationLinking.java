package eu.wdaqua.qanary.earlrel;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;


@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class EarlRelationLinking extends QanaryComponent {

	private static final Logger logger = LoggerFactory.getLogger(EarlRelationLinking.class);

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception{
		logger.info("process: {}", myQanaryMessage);
		// TODO: implement processing of question
		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion(myQanaryMessage);
		String myQuestion = myQanaryQuestion.getTextualRepresentation();
		//String myQuestion = "Who is the president of Russia?";
		ArrayList<Selection> selections = new ArrayList<Selection>();
		logger.info("Question {}", myQuestion);
		String thePath = URLEncoder.encode(myQuestion, "UTF-8");
		logger.info("thePath {}", thePath);
		JSONObject msg = new JSONObject();
		msg.put("nlquery", myQuestion);
		String jsonThePath = msg.toString();
		logger.info("\n\r\n\r==== msg: {}",msg);
		logger.info("==== jsonThePath: {}\n\r\n\r",jsonThePath);
		try {
			final JSONObject requestJSON = new JSONObject();
			requestJSON.put("nlquery", myQuestion);
			requestJSON.put("pagerankflag", true);



			HttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost("https://earldemo.sda.tech/earl/api/processQuery");
			StringEntity requestEntity = new StringEntity(requestJSON.toString(), ContentType.APPLICATION_JSON);
			httpPost.setEntity(requestEntity);
			HttpResponse httpResponse = httpclient.execute(httpPost);
			HttpEntity entity = httpResponse.getEntity();
			entity.getContent();
			String responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);
			System.out.println("Response body: " + responseBody);


			logger.info("\n\r==== textEL: {}\n\r\n\r",responseBody);

			final JSONObject response = new JSONObject(responseBody);
			final JSONObject lists = response.getJSONObject("rerankedlists");
			final JSONArray chunks = response.getJSONArray("chunktext");

			// check if relation identified in chunks
			for (int i = 0; i < chunks.length(); i++) {
				JSONObject chunk = chunks.getJSONObject(i);
				String chunkClass = chunk.getString("class").toLowerCase();
				if(chunkClass.equals("relation")) {
					Selection s = new Selection();
					final int start = chunk.getInt("surfacestart");
					final int end = start + chunk.getInt("surfacelength") - 1;
					final String link = lists.getJSONArray(i+"").getJSONArray(0).getString(1);

					s.begin = start;
					s.end = end;
					s.link = link;

					System.out.println(start+" "+end+" "+link);
					selections.add(s);
				}
			}
		}	
		catch (JSONException e) {
			logger.error("Except: {}", e);

		}
		catch (IOException e) {
			 logger.error("Except: {}", e);
			// TODO Auto-generated catch block
		} 
		catch (Exception e) {
			 logger.error("Except: {}", e);
			// TODO Auto-generated catch block
		}


		for (Selection s : selections) {
            String sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
                    + "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
                    + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " //
                    + "INSERT { " + "GRAPH <" + myQanaryQuestion.getOutGraph() + "> { " //
                    + "  ?a a qa:AnnotationOfRelation . " //
                    + "  ?a oa:hasTarget [ " //
                    + "           a    oa:SpecificResource; " //
                    + "           oa:hasSource    <" + myQanaryQuestion.getUri() + ">; " //
                    + "           oa:hasSelector  [ " //
                    + "                    a oa:TextPositionSelector ; " //
                    + "                    oa:start \"" + s.begin + "\"^^xsd:nonNegativeInteger ; " //
                    + "                    oa:end  \"" + s.end + "\"^^xsd:nonNegativeInteger  " //
                    + "           ] " //
                    + "  ] . " //
                    + "  ?a oa:hasBody <" + s.link + "> ;" //
                    + "     oa:annotatedBy <http://earlrelationlinker.com> ; " //
                    + "	    oa:AnnotatedAt ?time  " + "}} " //
                    + "WHERE { " //
                    + "  BIND (IRI(str(RAND())) AS ?a) ."//
                    + "  BIND (now() as ?time) " //
                    + "}";
            logger.debug("Sparql query: {}", sparql);
            myQanaryUtils.updateTripleStore(sparql,myQanaryMessage.getEndpoint().toString());
		}

		logger.info("store data in graph {}", myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		// TODO: insert data in QanaryMessage.outgraph

		logger.info("apply vocabulary alignment on outgraph");
		// TODO: implement this (custom for every component)

		return myQanaryMessage;
	}
	class Selection {
		public int begin;
		public int end;
		public String link;
	}
}

