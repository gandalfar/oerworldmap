package services;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import models.Resource;

import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ElasticsearchRepositoryTest {

  private static Resource mResource1;
  private static Resource mResource2;
  private static ElasticsearchClient mElClient;
  private static ElasticsearchRepository mRepo;
  private static final ElasticsearchConfig esConfig = new ElasticsearchConfig();

  @BeforeClass
  public static void setup() throws IOException {
    mResource1 = new Resource(esConfig.getType(), UUID.randomUUID().toString());
    mResource1.put("name", "oeruser1");
    mResource1.put("worksFor", "oerknowledgecloud.org");

    mResource2 = new Resource(esConfig.getType(), UUID.randomUUID().toString());
    mResource2.put("name", "oeruser2");
    mResource2.put("worksFor", "unesco.org");

    mElClient = new ElasticsearchClient(nodeBuilder().settings(esConfig.getClientSettingsBuilder()).local(true).node()
        .client());
    cleanIndex();
    mRepo = new ElasticsearchRepository(mElClient);
  }

  // create a new clean ElasticsearchIndex for this Test class
  private static void cleanIndex() {
    if (mElClient.hasIndex(esConfig.getIndex())){
      mElClient.deleteIndex(esConfig.getIndex());
    }
    mElClient.createIndex(esConfig.getIndex());
  }

  @Test
  public void testAddAndQueryResources() throws IOException {
    mRepo.addResource(mResource1);
    mRepo.addResource(mResource2);
    mElClient.refreshIndex(esConfig.getIndex());

    List<Resource> resourcesGotBack = mRepo.query(esConfig.getType());

    Assert.assertTrue(resourcesGotBack.contains(mResource1));
    Assert.assertTrue(resourcesGotBack.contains(mResource2));
  }
  
  @Test
  public void testAddAndEsQueryResources() throws IOException {
    final String aQueryString = "_search?@*:*";
    try {
      // TODO : this test currently presumes that there is some data existent in your elasticsearch
      // instance. Otherwise it will fail. This restriction can be overturned when a parallel method
      // for the use of POST is introduced in ElasticsearchRepository.
      List<Resource> result = mRepo.esQuery(aQueryString);
      Assert.assertTrue(!result.isEmpty());
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }
}
