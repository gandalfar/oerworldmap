package services.repository;

import com.typesafe.config.Config;
import helpers.JsonLdConstants;

import java.io.IOException;
import java.util.*;

import javax.annotation.Nonnull;

import models.Record;
import models.Resource;

import models.ResourceList;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.json.simple.parser.ParseException;
import play.Logger;
import services.ElasticsearchConfig;
import services.ElasticsearchProvider;

public class ElasticsearchRepository extends Repository implements Readable, Writable, Queryable, Aggregatable {

  final private ElasticsearchProvider elasticsearch;

  final public static String DEFAULT_TYPE = "Thing";

  public ElasticsearchRepository(Config aConfiguration) {
    super(aConfiguration);
    ElasticsearchConfig elasticsearchConfig = new ElasticsearchConfig(aConfiguration);
    Settings settings = ImmutableSettings.settingsBuilder()
        .put(elasticsearchConfig.getClientSettings()).build();
    Client client = new TransportClient(settings)
        .addTransportAddress(new InetSocketTransportAddress(elasticsearchConfig.getServer(), 9300));
    elasticsearch = new ElasticsearchProvider(client, elasticsearchConfig);
  }

  public ElasticsearchProvider getElasticsearchProvider() {
    return this.elasticsearch;
  }

  @Override
  public void addResource(@Nonnull final Resource aResource, @Nonnull final String aType)
      throws IOException {
    String id = (String) aResource.get(JsonLdConstants.ID);
    if (StringUtils.isEmpty(id)) {
      id = UUID.randomUUID().toString();
    }
    elasticsearch.addJson(aResource.toString(), id, aType);
  }

  @Override
  public Resource getResource(@Nonnull String aId) {
    return Resource.fromMap(elasticsearch.getDocument("_all", aId));
  }

  @Override
  public List<Resource> getAll(@Nonnull String aType) throws IOException {
    List<Resource> resources = new ArrayList<Resource>();
    for (Map<String, Object> doc : elasticsearch.getAllDocs(aType)) {
      resources.add(Resource.fromMap(doc));
    }
    return resources;
  }


  @Override
  public Resource deleteResource(@Nonnull String aId) {
    // TODO: delete mentioned resources?
    Resource resource = getResource(aId);
    if (null == resource) {
      return null;
    }

    // FIXME: check why deleting from _all is not possible, remove dependency on
    // Record class
    String type = ((Resource) resource.get(Record.RESOURCEKEY)).get(JsonLdConstants.TYPE)
        .toString();
    Logger.info("DELETING " + type + aId);

    if (elasticsearch.deleteDocument(type, aId)) {
      return resource;
    } else {
      return null;
    }
  }

  @Override
  public Resource aggregate(@Nonnull AggregationBuilder<?> aAggregationBuilder) throws IOException {
    Resource aggregations = Resource.fromJson(elasticsearch.getAggregation(aAggregationBuilder)
        .toString());
    if (null == aggregations) {
      return null;
    }
    return (Resource) aggregations.get("aggregations");
  }

  /**
   * This search method is designed to be able to make use of the complete
   * Elasticsearch query syntax, as described in
   * http://www.elasticsearch.org/guide
   * /en/elasticsearch/reference/current/search-uri-request.html .
   *
   * @param  aQueryString A string describing the query
   * @return A resource resembling the result set of resources matching the criteria given in the query string
   * @throws IOException
   * @throws ParseException
   */
  @Override
  public ResourceList query(@Nonnull String aQueryString, int aFrom, int aSize, String aSortOrder) throws IOException, ParseException {
    // FIXME: hardcoded access restriction to newsletter-only unsers, criteria:
    // has no unencrypted email address
    String filteredQueryString = aQueryString
        .concat(" AND ((about.@type:Article OR about.@type:Organization OR about.@type:Action OR about.@type:Service)")
        .concat(" OR (about.@type:Person AND about.email:*))");
    ResourceList resourceList = new ResourceList();
    SearchResponse response = elasticsearch.esQuery(filteredQueryString, aFrom, aSize, aSortOrder);
    resourceList.setItemsPerPage(aSize);
    long totalHits = response.getHits().getTotalHits();
    String nextPage = aFrom + aSize < totalHits
        ? "?q=".concat(aQueryString).concat("&from=").concat(Long.toString(aFrom + aSize)).concat("&size=")
        .concat(Long.toString(aSize)) : null;
    String previousPage = aFrom - aSize >= 0
        ? "?q=".concat(aQueryString).concat("&from=").concat(Long.toString(aFrom - aSize)).concat("&size=")
        .concat(Long.toString(aSize)) : null;
    String firstPage = "?q=".concat(aQueryString).concat("&from=0&size=").concat(Long.toString(aSize));
    String lastPage = "?q=".concat(aQueryString).concat("&from=").concat(Long.toString((totalHits / aSize) * aSize))
        .concat("&size=").concat(Long.toString(aSize));
    resourceList.setTotalItems(totalHits);
    resourceList.setNextPage(nextPage);
    resourceList.setPreviousPage(previousPage);
    resourceList.setFirstPage(firstPage);
    resourceList.setLastPage(lastPage);
    Iterator<SearchHit> searchHits = response.getHits().iterator();
    List<Resource> matches = new ArrayList<>();
    while (searchHits.hasNext()) {
      Resource match = Resource.fromMap(searchHits.next().sourceAsMap());
      matches.add(match);
    }
    resourceList.setItems(matches);
    return resourceList;
  }
}
