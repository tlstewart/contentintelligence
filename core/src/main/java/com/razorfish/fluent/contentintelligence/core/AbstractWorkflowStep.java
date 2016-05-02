package com.razorfish.fluent.contentintelligence.core;

import com.day.cq.dam.commons.process.AbstractAssetWorkflowProcess;
import javax.jcr.Node;
import java.io.IOException;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;

import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;

public abstract class AbstractWorkflowStep extends AbstractAssetWorkflowProcess {
	private static final double MIN_CONFIDENCE = 0.75;
	protected final Logger log = LoggerFactory.getLogger(this.getClass());
	 private String _requestUri = "https://gateway.watsonplatform.net/tone-analyzer-beta/api/v3/tone";
     private static String IBM_API_USER_NAME = "IBM_API_USER_NAME";
     private String _apiUserName = "";
     private static String IBM_API_PASSWORD = "IBM_API_PASSWORD";
     private String _apiPassword = "";

	
	public AbstractWorkflowStep() {
		super();
	}
	
	 public void LoadAPIKey()
     {
          String value = System.getenv(IBM_API_USER_NAME);
          this._apiUserName= value;
          String val = System.getenv(IBM_API_PASSWORD);
          this._apiPassword= val;
     }

	
    protected String handleRequestResponse(String text) throws IOException, JSONException {

        String parameterString = "version=2016-02-11";

        if (this._apiUserName.length()==0) {
                LoadAPIKey();
        }
        URL url = new URL(_requestUri + "?" + parameterString);
        log.info(url.toString());

        String userPassword = this._apiUserName + ":" + this._apiPassword;
        @SuppressWarnings("restriction")
		String encoding = java.util.Base64.getEncoder().encodeToString(userPassword.getBytes());
        byte[] postDataBytes =  new JSONObject().put("text", text).toString().getBytes("UTF-8");
        
		HttpURLConnection handle = (HttpURLConnection) url.openConnection();
		handle.setRequestProperty("Authorization", "Basic " + encoding);
		handle.setDoOutput(true);

		handle.addRequestProperty("Content-Type", "application/json");
		handle.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
		
		DataOutputStream ostream = new DataOutputStream(handle.getOutputStream());
		ostream.write(postDataBytes);
		ostream.close();

		if (handle.getResponseCode() == 200) {
		
		        BufferedReader reader = new BufferedReader(new InputStreamReader(handle.getInputStream()));
		        StringBuilder stringBuilder = new StringBuilder();
		
		        String line = null;
		        while ((line = reader.readLine()) != null)
		        {
		          stringBuilder.append(line + "\n");
		        }
		        return stringBuilder.toString();
		} else {
		        BufferedReader reader = new BufferedReader(new InputStreamReader(handle.getErrorStream()));
		        StringBuilder stringBuilder = new StringBuilder();
		
		        String line = null;
		        while ((line = reader.readLine()) != null)
		        {
		          stringBuilder.append(line + "\n");
		        }
		        log.error("Error result " + stringBuilder.toString() );
		        return "";
		}
    }

    
	protected void addMetaData(WorkItem workItem, WorkflowSession wfSession, final Asset asset, TagManager tagManager,
			String[] tagArray) throws Exception {
		final ResourceResolver resolver = getResourceResolver(wfSession.getSession());
		final Resource assetResource = asset.adaptTo(Resource.class);
		final Resource metadata = resolver.getResource(assetResource,
				JcrConstants.JCR_CONTENT + "/" + DamConstants.METADATA_FOLDER);

		
		/*
		Tag[] existing_tags = tagManager.getTags(assetResource);
		
		if (existing_tags.length > 0) {
			String[] existing_tags_array = new String[existing_tags.length];
			int i = 0;
			for (Tag existing_tag : existing_tags) {
				log.info("existing tag " + existing_tag.getPath());
				existing_tags_array[i++] = existing_tag.getPath();
			}
			tagArray =  join(existing_tags_array, tagArray);
		} else {
			log.info("no existing tags found");
		}
		*/
	
		if (null != metadata) {
			final Node metadataNode = metadata.adaptTo(Node.class);
			
			ValueMap properties = metadata.adaptTo(ValueMap.class);
			
			String[] existing_tags = properties.get("cq:tags", String[].class);
			if (existing_tags!=null && existing_tags.length > 0) {
				log.info( existing_tags.length + " existing tags found");
				tagArray =  join(existing_tags, tagArray);
			} else {
				log.info("no existing tags found");
			}
			log.info( tagArray.length + " total tags ");
			
			metadataNode.setProperty("cq:tags",tagArray);
			
			metadataNode.getSession().save();
			log.info("added or updated tags");
		} else {
			log.warn("execute: failed setting metdata for asset [{}] in workflow [{}], no metdata node found.",
					asset.getPath(), workItem.getId());
		}
	}
	
	/**
     * create individual tags if they don't exist yet
     * @param tagManager
     * @param entities
     * @return
     * @throws InvalidTagFormatException
     */
    protected String[] createTags(TagManager tagManager,JSONArray entities, String namespace, String container, String category)
                    throws InvalidTagFormatException, JSONException {
            Tag tag;
            String tagArray[] = new String[entities.length()];
            int index = 0;

            for (int i=0; i<entities.length();i++) {

                    JSONObject label = (JSONObject) entities.get(i);

                    log.info("found label " + label.getString("tone_name") + " with score : " + label.getString("score"));


                    if (label.getDouble("score")>MIN_CONFIDENCE) {
	                    tag = tagManager.createTag(namespace+container + "/" + category + "/" + label.getString("tone_id").replaceAll(" ", "_").toLowerCase(), label.getString("tone_id"),
	                                    "Auto detected : " + label.getString("tone_id"), true);
	                    tagArray[index] = tag.getNamespace().getName() + ":" + tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
	
	                    log.info(tag.getNamespace().getName() + ":" + tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
	
	                    index++;
                    }

            }

            return tagArray;
    }



	
	/**
	 * Join two arrays
	 * @param String1
	 * @param String2
	 * @return
	 */
	protected String[] join(String[] String1, String[] String2) {
		String[] allStrings = new String[String1.length + String2.length];

		System.arraycopy(String1, 0, allStrings, 0, String1.length);
		System.arraycopy(String2, 0, allStrings, String1.length, String2.length);

		return allStrings;
	}


}
