package com.razorfish.fluent.contentintelligence.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONArray;
import org.osgi.framework.Constants;

import com.day.cq.dam.api.Asset;
import com.adobe.cq.dam.cfm.*;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.metadata.MetaDataMap;
import com.day.cq.tagging.JcrTagManagerFactory;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;

//This is a component so it can provide or consume services
@Component

@Service

@Properties({ @Property(name = Constants.SERVICE_DESCRIPTION, value = "Content Intelligence with Watson"),
                @Property(name = Constants.SERVICE_VENDOR, value = "Razorfish"),
                @Property(name = "process.label", value = "Bluemix.") })

public class ContentIntelligenceWorkflowStep extends AbstractWorkflowStep {

    /** Default log. */
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String NAMESPACE = "/etc/tags/bluemix";
    private static final String CONTAINER = "/label";
    private static final int MAX_LABELS = 10;
    @Reference
    JcrTagManagerFactory tmf;

    public void execute(WorkItem workItem, WorkflowSession wfSession, MetaDataMap args) throws WorkflowException {

            try {
                    log.info("ContentIntelligenceWorkflowStep workflow step in execute method");
                    final Asset asset = getAssetFromPayload(workItem, wfSession.getSession());

                    // create tag manager
                    TagManager tagManager = tmf.getTagManager(wfSession.getSession());
                    Tag superTag = tagManager.resolve(NAMESPACE+CONTAINER);
                    Tag tag = null;

                    if (superTag == null) {
                            tag = tagManager.createTag(NAMESPACE+CONTAINER, "labels", "autodetected labels", true);
                            log.info("Tag Name Space created : ", tag.getPath());
                    } else {
                            tag = superTag;
                    }

                    log.info("Asset " + asset.getName() + " path " + asset.getPath());
                   // byte[] data = new byte[(int) asset.getOriginal().getSize()];
                    InputStream is =  asset.getOriginal().getStream();
                    
                    BufferedInputStream bis = new BufferedInputStream(is);
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                        byte b = (byte) result;
                        buf.write(b);
                        result = bis.read();
                    }

                    System.out.println("plain text: " + buf.toString());

                    
                    //CharBuffer cb = ByteBuffer.wrap(data).asCharBuffer();
                    String content = buf.toString();
                    log.info(content);
                    
                    String tone = handleRequestResponse(content);
                	
                	log.info(tone);
                	 if (tone.length()>0) {
                		 JSONObject jsonObject = new JSONObject(tone);
                		 JSONObject doc = (JSONObject) jsonObject.get("document_tone");
                		 JSONArray cat = (JSONArray) doc.get("tone_categories");
                		 
                		 JSONObject tones_group = (JSONObject) cat.get(0);
                		 JSONArray tones = (JSONArray) tones_group.get("tones");
                		 String category = tones_group.getString("category_id");
                		 String[] tagArray = createTags(tagManager, tones,NAMESPACE, CONTAINER, category);
                		 addMetaData(workItem, wfSession, asset, tagManager, tagArray);
                		 
                		 tones_group = (JSONObject) cat.get(1);
                		 tones = (JSONArray) tones_group.get("tones");
                		 category = tones_group.getString("category_id");
                		 tagArray = createTags(tagManager, tones,NAMESPACE, CONTAINER, category);
                		 addMetaData(workItem, wfSession, asset, tagManager, tagArray);
                		 
                		 tones_group = (JSONObject) cat.get(2);
                		 tones = (JSONArray) tones_group.get("tones");
                		 category = tones_group.getString("category_id");
                		 tagArray = createTags(tagManager, tones,NAMESPACE, CONTAINER, category);
                		 addMetaData(workItem, wfSession, asset, tagManager, tagArray);
              
                	 }
                	/*
                    ContentFragment cf = asset.adaptTo(ContentFragment.class);
                    
                    if (cf!=null) {
                    	
	                    for(Iterator<ContentElement> i = cf.getElements(); i.hasNext(); ) {
	                    	ContentElement ce = i.next();
	                    	String content = ce.getContent();
	                    	String tone = handleRequestResponse(content);
	                    	
	                    	log.info(tone);
	                    	
	
	                    }
                    } else {
                    	log.warn("ContentFragment is null");
                    }
                    */
                    /*
                    byte[] data = new byte[(int) asset.getOriginal().getSize()];
                    int numbytesread = asset.getOriginal().getStream().read(data);
                    log.info("Read : {} of {}", numbytesread, asset.getOriginal().getSize() );

                    String s = ""; //ImageGetRankedImageKeywords(data);
                    if (s.length()>0) {
                            JSONObject jsonObject = new JSONObject(s);

                            JSONArray labels = (JSONArray) jsonObject.get("imageKeywords");

                            String[] tagArray = createTags(tagManager, labels,NAMESPACE, CONTAINER);

                            addMetaData(workItem, wfSession, asset, tagManager, tagArray);
                    }
                    */

            }

            catch (Exception e) {
                    log.error("Error in execution" + e);
                    e.printStackTrace();
            }
    }

}



