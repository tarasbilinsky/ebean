package com.avaje.ebeaninternal.server.core;


import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.FetchConfig;
import com.avaje.ebean.Query;
import com.avaje.ebeaninternal.api.SpiQuery;
import com.avaje.ebeaninternal.server.querydefn.DefaultOrmQuery;
import com.avaje.ebeaninternal.server.querydefn.OrmQueryDetail;
import com.avaje.tests.model.basic.Order;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultServer_createOrmQueryRequestTest extends BaseTestCase {

  static DefaultServer defaultServer = (DefaultServer) Ebean.getDefaultServer();

  Query<Order> query() {
    return defaultServer.find(Order.class);
  }

  OrmQueryRequest<Order> queryRequest(Query<Order> query) {
    return (OrmQueryRequest<Order>) defaultServer.createQueryRequest(SpiQuery.Type.LIST, query, null);
  }

  OrmQueryDetail detail(Query<Order> query) {
    return queryRequest(query).getQuery().getDetail();
  }

  @Test
  public void when_empty_then_same() {

    assertSame(detail(query()), detail(query()));
  }

  @Test
  public void select_all_then_same() {

    assertSame(detail(query().select("*")), detail(query().select("*")));
  }

  @Test
  public void when_propertiesInOrder_then_same() {

    assertSame(detail(query().select("id,name")), detail(query().select("id,name")));
  }

  @Test
  public void when_propertiesInDifferentOrder_then_different() {

    assertDifferent(detail(query().select("id,name")), detail(query().select("name, id")));

    // but same from autotune perspective
    assertThat(detail(query().select("id,name")).isAutoTuneEqual(detail(query().select("name, id")))).isTrue();
  }

  @Test
  public void when_additional_fetch_then_different() {

    assertDifferent(detail(query().select("id,name")), detail(query().select("id,name").fetch("details")));
  }

  @Test
  public void when_same_fetch_then_same() {

    assertSame(detail(query().select("id,name").fetch("details")), detail(query().select("id,name").fetch("details")));
  }

  @Test
  public void when_fetch_order_different_then_different() {

    assertDifferent(detail(query().select("id,name").fetch("details").fetch("customer")),
        detail(query().select("id,name").fetch("customer").fetch("details")));
  }

  @Test
  public void when_extra_queryFetchToMany_then_same() {

    assertDifferent(detail(query().select("id,name").fetch("customer")),
        detail(query().select("id,name").fetch("customer").fetch("details", new FetchConfig().query())));
  }

  @Test
  public void when_extra_queryToOne_fetch_then_different() {

    // with the fetch of customer the foreign key must be added to the root query
    assertDifferent(detail(query().select("id,name")),
        detail(query().select("id,name").fetch("customer", new FetchConfig().query())));
  }

  @Test
  public void when_additional_fetch_V2_then_different() {

    assertDifferent(detail(query().select("id,name")), detail(query().select("id,name").fetch("customer", "id")));
  }


  @Test
  public void when_fetchConfig_then_differentPlan() throws Exception {

    DefaultOrmQuery<Order> query1 = (DefaultOrmQuery<Order>) Ebean.find(Order.class)
        .select("status, shipDate")
        .fetch("details", "orderQty, unitPrice", new FetchConfig().query())
        .fetch("details.product", "sku, name");


    DefaultOrmQuery<Order> query2 = (DefaultOrmQuery<Order>) Ebean.find(Order.class)
        .select("status, shipDate")
        .fetch("details", "orderQty, unitPrice")
        .fetch("details.product", "sku, name");

    assertDifferent(detail(query1), detail(query2));
  }

  @Test
  public void testJoinOrder_when_fetchJoins_expect_detailJoinsPreserveOrder() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("customer", "name")
        .fetch("details");

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer", "details");
  }

  @Test
  public void testJoinOrder_when_fetchJoinsAndWhere_expect_fetchJoinsOnlyInFetchPaths() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("details")
        .where().eq("customer.name", "rob").query();

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("details");
  }

  @Test
  public void testJoinOrder_when_queryFetch_expect_getFetchPaths_doesNotIncludeQueryJoin() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("customer", "name")
        .fetch("details", new FetchConfig().query());

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer");
  }

  @Test
  public void testJoinOrder_when_lazyFetch_expect_getFetchPaths_doesNotIncludeQueryJoin() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("customer", "name")
        .fetch("details", new FetchConfig().lazy());

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer");
  }

  @Test
  public void testJoinOrder_when_lazyFetchAndHasChildren_expect_getFetchPaths_doesNotIncludeJoinOrChild() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("customer", "name")
        .fetch("details", new FetchConfig().lazy())
        .fetch("details.product");

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer");
  }

  @Test
  public void testJoinOrder_when_fetchMany_expect_getFetchPaths_containsAllInOrder() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("details")
        .fetch("details.product")
        .fetch("customer", "name");

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("details", "details.product", "customer");
  }

  @Test
  public void testJoinOrder_when_queryJoin_expect_getFetchPaths_excludesQueryJoinAndChildren() {

    Query<Order> query = Ebean.find(Order.class)
        .select("status, orderDate")
        .fetch("details", new FetchConfig().query())
        .fetch("details.product")
        .fetch("customer", "name");

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer");
  }

  @Test
  public void test_removeJoinToMany_when_multipleManyPaths() {

    Query<Order> query = Ebean.find(Order.class)
        .fetch("details")
        .fetch("details.product")
        .fetch("customer")
        .fetch("customer.contacts"); // second many path

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("details", "details.product", "customer");
  }

  @Test
  public void test_removeAllJoinToMany_when_firstRow() {

    Query<Order> query = Ebean.find(Order.class)
        .setFirstRow(1)
        .fetch("details") // many path
        .fetch("details.product")
        .fetch("customer")
        .fetch("customer.contacts"); // many path

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer");
  }

  @Test
  public void test_removeAllJoinToMany_when_maxRows() {

    Query<Order> query = Ebean.find(Order.class)
        .setMaxRows(1)
        .fetch("details") // many path
        .fetch("details.product")
        .fetch("customer")
        .fetch("customer.contacts"); // many path

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer");
  }

  @Test
  public void test_removeJoinToMany_when_filterMany() {

    Query<Order> query = Ebean.find(Order.class)
        .fetch("details")
        .fetch("details.product")
        .fetch("customer")
        .fetch("customer.contacts")
        .filterMany("details").eq("orderQuantity", 10)
        .query();

    OrmQueryRequest<Order> queryRequest = queryRequest(query);
    OrmQueryDetail detail = queryRequest.getQuery().getDetail();

    assertThat(detail.getFetchPaths()).containsExactly("customer", "customer.contacts");
  }

  private void assertSame(OrmQueryDetail detail1, OrmQueryDetail detail2) {
    assertThat(detail1.isSameByPlan(detail2)).isTrue();
    assertThat(detail1.queryPlanHash()).isEqualTo(detail2.queryPlanHash());
  }

  private void assertDifferent(OrmQueryDetail detail1, OrmQueryDetail detail2) {
    assertThat(detail1.isSameByPlan(detail2)).isFalse();
    assertThat(detail1.queryPlanHash()).isNotEqualTo(detail2.queryPlanHash());
  }
}