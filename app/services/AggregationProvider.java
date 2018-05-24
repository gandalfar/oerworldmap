package services;

import helpers.JsonLdConstants;
import models.Record;
import models.Resource;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.util.Arrays;
import java.util.List;

/**
 * @author fo
 */
public class AggregationProvider {

  public static AggregationBuilder<?> getTypeAggregation(int aSize) {
    return AggregationBuilders.terms("about.@type").size(aSize).field("about.@type").minDocCount(0)
        .exclude("Concept|ConceptScheme|Comment|LikeAction|LighthouseAction").order(Terms.Order.term(false));
  }

  public static AggregationBuilder<?> getServiceLanguageAggregation(int aSize) {
    return AggregationBuilders.terms("about.availableChannel.availableLanguage").size(aSize)
        .field("about.availableChannel.availableLanguage");
  }

  public static AggregationBuilder<?> getServiceByFieldOfEducationAggregation(int aSize) {
    return AggregationBuilders.terms("about.about.@id").size(aSize).field("about.about.@id");
  }

  public static AggregationBuilder<?> getServiceByFieldOfEducationAggregation(List<String> anIdList, int aSize) {
    return AggregationBuilders.terms("about.about.@id").size(aSize)
        .field("about.about.@id")
        .include(StringUtils.join(anIdList, '|'));
  }

  public static AggregationBuilder<?> getServiceByTopLevelFieldOfEducationAggregation() {
    String[] topLevelIds = new String[] {
      "https://w3id.org/class/esc/n00",
      "https://w3id.org/class/esc/n01",
      "https://w3id.org/class/esc/n02",
      "https://w3id.org/class/esc/n03",
      "https://w3id.org/class/esc/n04",
      "https://w3id.org/class/esc/n05",
      "https://w3id.org/class/esc/n06",
      "https://w3id.org/class/esc/n07",
      "https://w3id.org/class/esc/n08",
      "https://w3id.org/class/esc/n09",
      "https://w3id.org/class/esc/n10"
    };
    return getServiceByFieldOfEducationAggregation(Arrays.asList(topLevelIds), 0);
  }

  public static AggregationBuilder<?> getServiceByGradeLevelAggregation(int aSize) {
    return AggregationBuilders.terms("about.audience.@id").size(aSize)
        .field("about.audience.@id");
  }

  public static AggregationBuilder<?> getKeywordsAggregation(int aSize) {
    return AggregationBuilders.terms("about.keywords").size(aSize)
      .field("about.keywords");
  }

  public static AggregationBuilder<?> getByCountryAggregation(int aSize) {
    return AggregationBuilders
        .terms("feature.properties.location.address.addressCountry").field("feature.properties.location.address.addressCountry").size(aSize)
        .subAggregation(AggregationBuilders.terms("by_type").field("about.@type"))
        .subAggregation(AggregationBuilders
          .filter("champions")
          .filter(QueryBuilders.existsQuery(Record.RESOURCE_KEY + ".countryChampionFor")));
  }

  public static AggregationBuilder<?> getForCountryAggregation(String aId, int aSize) {
    return AggregationBuilders.global("country").subAggregation(
      AggregationBuilders
        .terms("feature.properties.location.address.addressCountry").field("feature.properties.location.address.addressCountry").include(
        aId)
        .size(aSize)
        .subAggregation(AggregationBuilders.terms("by_type").field("about.@type")
          .exclude("Concept|ConceptScheme|Comment|LikeAction|LighthouseAction"))
        .subAggregation(AggregationBuilders
          .filter("champions")
          .filter(QueryBuilders.existsQuery(Record.RESOURCE_KEY + ".countryChampionFor"))
          .subAggregation(AggregationBuilders.topHits("country_champions")))
        .subAggregation(AggregationBuilders
          .filter("reports")
          .filter(QueryBuilders
            .matchQuery(Record.RESOURCE_KEY + ".keywords", "countryreport:".concat(aId)))
          .subAggregation(AggregationBuilders.topHits("country_reports")))
    );
  }

  public static AggregationBuilder<?> getLicenseAggregation(int aSize) {
    return AggregationBuilders.terms("about.license.@id").size(aSize)
      .field("about.license.@id");
  }

  public static AggregationBuilder<?> getProjectByLocationAggregation(int aSize) {
    return AggregationBuilders.terms("about.agent.location.address.addressCountry").size(aSize)
      .field("about.agent.location.address.addressCountry");
  }

  public static AggregationBuilder<?> getFunderAggregation(int aSize) {
    return AggregationBuilders.terms("about.isFundedBy.isAwardedBy.@id").size(aSize)
      .field("about.isFundedBy.isAwardedBy.@id");
  }

  public static AggregationBuilder<?> getEventCalendarAggregation() {
    return AggregationBuilders
        .dateHistogram("about.startDate.GTE")
        .field("about.startDate")
        .interval(DateHistogramInterval.MONTH).subAggregation(AggregationBuilders.topHits("about.@id")
            .setFetchSource(new String[]{"about.@id", "about.@type", "about.name", "about.startDate", "about.endDate",
              "feature.properties.location"}, null)
          .addSort(new FieldSortBuilder("about.startDate").order(SortOrder.ASC).unmappedType("string"))
          .setSize(Integer.MAX_VALUE)
      ).order(Histogram.Order.KEY_DESC).minDocCount(1);
  }

  public static AggregationBuilder<?> getRegionAggregation(int aSize, String iso3166Scope) {
    return AggregationBuilders.terms("feature.properties.location.address.addressRegion")
      .field("feature.properties.location.address.addressRegion")
      .include(iso3166Scope + "\\....?")
      .size(aSize);
  }

  public static AggregationBuilder<?> getLikeAggregation(int aSize) {
    return AggregationBuilders.terms("about.object.@id").size(aSize)
      .field("about.object.@id");
  }

  public static AggregationBuilder<?> getPrimarySectorsAggregation(int aSize) {
    return AggregationBuilders.terms("about.primarySector.@id").size(aSize)
      .field("about.primarySector.@id");
  }

  public static AggregationBuilder<?> getSecondarySectorsAggregation(int aSize) {
    return AggregationBuilders.terms("about.secondarySector.@id").size(aSize)
      .field("about.secondarySector.@id");
  }

  public static AggregationBuilder<?> getAwardAggregation(int aSize) {
    return AggregationBuilders.terms("about.award").size(aSize)
      .field("about.award");
  }

}
