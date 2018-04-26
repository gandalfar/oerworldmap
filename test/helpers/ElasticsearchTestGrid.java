package helpers;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import play.test.WithApplication;
import services.ElasticsearchConfig;
import services.repository.ElasticsearchRepository;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

public class ElasticsearchTestGrid extends WithApplication {

  protected static Config mConfig;
  protected static ElasticsearchRepository mRepo;
  protected static Settings mClientSettings;
  protected static TransportClient mClient;
  protected static ElasticsearchConfig mEsConfig;

  public static ElasticsearchRepository getEsRepo() {
    return mRepo;
  }

  @BeforeClass
  public static void setup() throws IOException {
    mConfig = ConfigFactory.parseFile(new File("conf/test.conf")).resolve();
    mRepo = new ElasticsearchRepository(mConfig);
    mEsConfig = mRepo.getConfig();

    final Settings.Builder builder = Settings.builder();
    mEsConfig.getClusterSettings().forEach((k, v) -> builder.put(k, v));
    mClientSettings = builder.build();

    mClient = new PreBuiltTransportClient(mClientSettings);
    mClient.addTransportAddress(new TransportAddress(InetAddress.getByName(mEsConfig.getServer()),
        Integer.valueOf(mEsConfig.getJavaPort())));
  }

  @AfterClass
  public static void tearDown() throws Exception {
    mEsConfig.tearDown();
  }

  @Before
  public void setupIndex() {
    ElasticsearchHelpers.cleanIndex(mRepo, mConfig.getString("es.index.name"));
  }

}
