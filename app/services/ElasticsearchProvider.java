package services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;

import play.Logger;

public class ElasticsearchProvider {

  private static ElasticsearchConfig mConfig;

  private Client mClient;

  /**
   * Initialize an instance with a specified non null Elasticsearch client.
   *
   * @param aClient
   * @param aConfig
   */
  public ElasticsearchProvider(@Nullable final Client aClient, ElasticsearchConfig aConfig) {
    mClient = aClient;
    mConfig = aConfig;
  }

  public String getIndex() {
    return mConfig.getIndex();
  }

  /**
   * Add a document consisting of a JSON String specified by a given UUID and a
   * given type.
   *
   * @param aJsonString
   */
  public void addJson(final String aJsonString, final String aUuid, final String aType) {
    mClient.prepareIndex(mConfig.getIndex(), aType, aUuid).setSource(aJsonString).execute()
        .actionGet();
  }

  /**
   * Get all documents of a given document type
   *
   * @param aType
   * @return a List of docs, each represented by a Map of String/Object.
   */
  public List<Map<String, Object>> getAllDocs(final String aType) {
    final int docsPerPage = 1024;
    int count = 0;
    SearchResponse response = null;
    final List<Map<String, Object>> docs = new ArrayList<Map<String, Object>>();
    while (response == null || response.getHits().hits().length != 0) {
      response = mClient.prepareSearch(mConfig.getIndex()).setTypes(aType)
          .setQuery(QueryBuilders.matchAllQuery()).setSize(docsPerPage).setFrom(count * docsPerPage)
          .execute().actionGet();
      for (SearchHit hit : response.getHits()) {
        docs.add(hit.getSource());
      }
      count++;
    }
    return docs;
  }

  public List<Map<String, Object>> getResources(final String aField, final Object aValue) {
    final int docsPerPage = 1024;
    int count = 0;
    SearchResponse response = null;
    final List<Map<String, Object>> docs = new ArrayList<>();
    while (response == null || response.getHits().hits().length != 0) {
      response = mClient.prepareSearch(mConfig.getIndex())
          .setQuery(QueryBuilders
              .queryString(aField.concat(":").concat(QueryParser.escape(aValue.toString()))))
          .setSize(docsPerPage).setFrom(count * docsPerPage).execute().actionGet();
      for (SearchHit hit : response.getHits()) {
        docs.add(hit.getSource());
      }
      count++;
    }
    return docs;
  }

  public SearchResponse getAggregation(final AggregationBuilder<?> aAggregationBuilder,
      QueryContext aQueryContext) {

    SearchRequestBuilder searchRequestBuilder = mClient.prepareSearch(mConfig.getIndex());

    AndFilterBuilder globalAndFilter = FilterBuilders.andFilter();
    if (!(null == aQueryContext)) {
      for (FilterBuilder contextFilter : aQueryContext.getFilters()) {
        globalAndFilter.add(contextFilter);
      }
    }

    SearchResponse response = searchRequestBuilder.addAggregation(aAggregationBuilder)
        .setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), globalAndFilter))
        .setSize(0).execute().actionGet();
    return response;

  }

  /**
   * Get a document of a specified type specified by an identifier.
   *
   * @param aType
   * @param aIdentifier
   * @return the document as Map of String/Object
   */
  public Map<String, Object> getDocument(@Nonnull final String aType,
      @Nonnull final String aIdentifier) {
    final GetResponse response = mClient.prepareGet(mConfig.getIndex(), aType, aIdentifier)
        .execute().actionGet();
    return response.getSource();
  }

  /**
   * Get a document of a specified type specified by a UUID.
   *
   * @param aType
   * @param aUuid
   * @return the document as Map of String/Object
   */
  public Map<String, Object> getDocument(@Nonnull final String aType, @Nonnull final UUID aUuid) {
    return getDocument(aType, aUuid.toString());
  }

  public boolean deleteDocument(@Nonnull final String aType, @Nonnull final String aIdentifier) {
    final DeleteResponse response = mClient.prepareDelete(mConfig.getIndex(), aType, aIdentifier)
        .execute().actionGet();
    return response.isFound();
  }

  /**
   * Verify if the specified index exists on the internal Elasticsearch client.
   *
   * @param aIndex
   * @return true if the specified index exists.
   */
  public boolean hasIndex(String aIndex) {
    return mClient.admin().indices().prepareExists(aIndex).execute().actionGet().isExists();
  }

  /**
   * Deletes the specified index. If the specified index does not exist, the
   * resulting IndexMissingException is caught.
   *
   * @param aIndex
   */
  public void deleteIndex(String aIndex) {
    try {
      mClient.admin().indices().delete(new DeleteIndexRequest(aIndex)).actionGet();
    } catch (IndexMissingException e) {
      Logger.error("Trying to delete index \"" + aIndex + "\" from Elasticsearch.");
      e.printStackTrace();
    }
  }

  /**
   * Creates the specified index. If the specified index does already exist, the
   * resulting ElasticsearchException is caught.
   *
   * @param aIndex
   */
  public void createIndex(String aIndex) {
    try {
      mClient.admin().indices().prepareCreate(aIndex).setSource(mConfig.getIndexConfigString())
          .execute().actionGet();
      mClient.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
    } catch (ElasticsearchException indexAlreadyExists) {
      Logger.error(
          "Trying to create index \"" + aIndex + "\" in Elasticsearch. Index already exists.");
      indexAlreadyExists.printStackTrace();
    } catch (IOException ioException) {
      Logger.error("Trying to create index \"" + aIndex
          + "\" in Elasticsearch. Couldn't read index config file.");
      ioException.printStackTrace();
    }
  }

  /**
   * Refreshes the specified index. If the specified index does not exist, the
   * resulting IndexMissingException is caught.
   *
   * @param aIndex
   */
  public void refreshIndex(String aIndex) {
    try {
      mClient.admin().indices().refresh(new RefreshRequest(aIndex)).actionGet();
    } catch (IndexMissingException e) {
      Logger.error("Trying to refresh index \"" + aIndex + "\" in Elasticsearch.");
      e.printStackTrace();
    }
  }

  public SearchResponse esQuery(@Nonnull String aQueryString, int aFrom, int aSize,
      String aSortOrder, Map<String, ArrayList<String>> aFilters, QueryContext aQueryContext) {

    SearchRequestBuilder searchRequestBuilder = mClient.prepareSearch(mConfig.getIndex());

    AndFilterBuilder globalAndFilter = FilterBuilders.andFilter();

    if (!(null == aQueryContext)) {
      searchRequestBuilder.setFetchSource(aQueryContext.getFetchSource(), null);
      for (FilterBuilder contextFilter : aQueryContext.getFilters()) {
        globalAndFilter.add(contextFilter);
      }
      for (AggregationBuilder<?> contextAggregation : aQueryContext.getAggregations()) {
        searchRequestBuilder.addAggregation(contextAggregation);
      }
    }

    if (!StringUtils.isEmpty(aSortOrder)) {
      String[] sort = aSortOrder.split(":");
      if (2 == sort.length) {
        searchRequestBuilder.addSort(sort[0],
            sort[1].toUpperCase().equals("ASC") ? SortOrder.ASC : SortOrder.DESC);
      } else {
        Logger.error("Invalid sort string: " + aSortOrder);
      }
    }

    if (!(null == aFilters)) {
      AndFilterBuilder aggregationAndFilter = FilterBuilders.andFilter();
      for (Map.Entry<String, ArrayList<String>> entry : aFilters.entrySet()) {
        // This could also be an OrFilterBuilder allowing to expand the result
        // list
        AndFilterBuilder andTermFilterBuilder = FilterBuilders.andFilter();
        for (String filter : entry.getValue()) {
          andTermFilterBuilder.add(FilterBuilders.termFilter(entry.getKey(), filter));
        }
        aggregationAndFilter.add(andTermFilterBuilder);
      }
      globalAndFilter.add(aggregationAndFilter);
    }

    QueryBuilder queryBuilder;
    if (!StringUtils.isEmpty(aQueryString)) {
      queryBuilder = QueryBuilders.queryString(aQueryString)
          .defaultOperator(QueryStringQueryBuilder.Operator.AND);
    } else {
      queryBuilder = QueryBuilders.matchAllQuery();
    }

    searchRequestBuilder.setQuery(QueryBuilders.filteredQuery(queryBuilder, globalAndFilter));

    return searchRequestBuilder.setFrom(aFrom).setSize(aSize).execute().actionGet();

  }
}
