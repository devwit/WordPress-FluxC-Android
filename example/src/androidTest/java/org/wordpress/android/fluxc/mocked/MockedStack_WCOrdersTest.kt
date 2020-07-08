package org.wordpress.android.fluxc.mocked

import com.google.gson.JsonObject
import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.TestUtils
import org.wordpress.android.fluxc.action.WCOrderAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.WCOrderNoteModel
import org.wordpress.android.fluxc.model.WCOrderShipmentTrackingModel
import org.wordpress.android.fluxc.module.ResponseMockingInterceptor
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.CoreOrderStatus
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.OrderRestClient
import org.wordpress.android.fluxc.store.WCOrderStore.AddOrderShipmentTrackingResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.DeleteOrderShipmentTrackingResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchHasOrdersResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderNotesResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderShipmentProvidersResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderShipmentTrackingsResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderStatusOptionsResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrdersCountResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrdersResponsePayload
import org.wordpress.android.fluxc.store.WCOrderStore.OrderErrorType
import org.wordpress.android.fluxc.store.WCOrderStore.RemoteOrderNotePayload
import org.wordpress.android.fluxc.store.WCOrderStore.RemoteOrderPayload
import org.wordpress.android.fluxc.store.WCOrderStore.SearchOrdersResponsePayload
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.inject.Inject
import kotlin.properties.Delegates.notNull

/**
 * Tests using a Mocked Network app component. Test the network client itself and not the underlying
 * network component(s).
 */
class MockedStack_WCOrdersTest : MockedStack_Base() {
    @Inject internal lateinit var orderRestClient: OrderRestClient
    @Inject internal lateinit var dispatcher: Dispatcher

    @Inject internal lateinit var interceptor: ResponseMockingInterceptor

    private var lastAction: Action<*>? = null
    private var countDownLatch: CountDownLatch by notNull()

    private val siteModel = SiteModel().apply {
        id = 5
        siteId = 567
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        mMockedNetworkAppComponent.inject(this)
        dispatcher.register(this)
        lastAction = null
    }

    @Test
    fun testOrderListFetchSuccess() {
        interceptor.respondWith("wc-orders-response-success.json")
        orderRestClient.fetchOrders(siteModel, 0)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDERS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrdersResponsePayload
        assertNull(payload.error)
        assertEquals(4, payload.orders.size)

        with(payload.orders[0]) {
            assertEquals(siteModel.id, localSiteId)
            assertEquals(949, remoteOrderId)
            assertEquals("949", number)
            assertEquals(CoreOrderStatus.PROCESSING.value, status)
            assertEquals("USD", currency)
            assertEquals("2018-04-02T14:57:39Z", dateCreated)
            assertEquals("44.00", total)
            assertEquals("0.00", totalTax)
            assertEquals("4.00", shippingTotal)
            assertEquals("stripe", paymentMethod)
            assertEquals("2018-04-11T18:58:54Z", datePaid)
            assertEquals("Credit Card (Stripe)", paymentMethodTitle)
            assertFalse(pricesIncludeTax)
            assertEquals(2, getLineItemList().size)
            assertEquals(40.0, getOrderSubtotal(), 0.01)
        }

        // Customer note
        with(payload.orders[1]) {
            assertEquals("test checkout field editor note", customerNote)
        }

        // Refunded order
        with(payload.orders[2]) {
            assertEquals(85.0, getOrderSubtotal(), 0.01)
            assertEquals("7.00", shippingTotal)
            assertEquals("92.00", total)
            assertEquals(-92.0, refundTotal, 0.01)
        }

        // Order with coupons
        with(payload.orders[3]) {
            assertEquals(60.0, getOrderSubtotal(), 0.01)
            assertEquals("7.59", shippingTotal)
            assertEquals("7.59", total)
            assertEquals("60.00", discountTotal)
            assertEquals("20\$off, 40\$off", discountCodes)
        }
    }

    @Test
    fun testSearchOrdersSuccess() {
        interceptor.respondWith("wc-orders-response-success.json")
        orderRestClient.searchOrders(siteModel, "", 0)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.SEARCHED_ORDERS, lastAction!!.type)
        val payload = lastAction!!.payload as SearchOrdersResponsePayload
        assertNull(payload.error)
        assertEquals(4, payload.orders.size)
    }

    @Test
    fun testSearchOrdersError() {
        interceptor.respondWithError("jetpack-tunnel-root-response-failure.json")
        orderRestClient.searchOrders(SiteModel(), "", 0)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.SEARCHED_ORDERS, lastAction!!.type)
        val payload = lastAction!!.payload as SearchOrdersResponsePayload
        assertNotNull(payload.error)
    }

    @Test
    fun testOrdersCountFetchSuccess() {
        val statusFilter = CoreOrderStatus.COMPLETED.value

        interceptor.respondWith("wc-order-count-response-success.json")
        orderRestClient.fetchOrderCount(siteModel, statusFilter)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDERS_COUNT, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrdersCountResponsePayload
        assertNull(payload.error)
        assertEquals(128, payload.count)
        assertEquals(statusFilter, payload.statusFilter)
    }

    @Test
    fun testOrderListFetchError() {
        interceptor.respondWithError("jetpack-tunnel-root-response-failure.json")
        orderRestClient.fetchOrders(SiteModel(), 0)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDERS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrdersResponsePayload
        assertNotNull(payload.error)
    }

    @Test
    fun testFetchSingleOrderSuccess() {
        val remoteOrderId = 88L
        interceptor.respondWith("wc-fetch-order-response-success.json")
        orderRestClient.fetchSingleOrder(siteModel, remoteOrderId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_SINGLE_ORDER, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteOrderPayload
        with(payload) {
            assertNull(error)
            assertEquals(remoteOrderId, order.remoteOrderId)
        }
    }

    @Test
    fun testFetchSingleOrderError() {
        val remoteOrderId = 88L

        interceptor.respondWithError("jetpack-tunnel-root-response-failure.json")
        orderRestClient.fetchSingleOrder(siteModel, remoteOrderId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_SINGLE_ORDER, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteOrderPayload
        assertNotNull(payload.error)
    }

    @Test
    fun testOrderStatusUpdateSuccess() {
        val originalOrder = WCOrderModel().apply {
            id = 8
            localSiteId = siteModel.id
            status = CoreOrderStatus.PROCESSING.value
            remoteOrderId = 88
            total = "15.00"
        }

        interceptor.respondWith("wc-order-update-response-success.json")
        orderRestClient.updateOrderStatus(originalOrder, siteModel, CoreOrderStatus.REFUNDED.value)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.UPDATED_ORDER_STATUS, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteOrderPayload
        with(payload) {
            assertNull(error)
            assertEquals(originalOrder.id, order.id)
            assertEquals(siteModel.id, order.localSiteId)
            assertEquals(originalOrder.remoteOrderId, order.remoteOrderId)
            assertEquals(CoreOrderStatus.REFUNDED.value, order.status)
        }
    }

    @Test
    fun testOrderStatusUpdateError() {
        val originalOrder = WCOrderModel().apply {
            id = 8
            localSiteId = siteModel.id
            status = CoreOrderStatus.PROCESSING.value
            remoteOrderId = 88
            total = "15.00"
        }

        val errorJson = JsonObject().apply {
            addProperty("error", "woocommerce_rest_shop_order_invalid_id")
            addProperty("message", "Invalid ID.")
        }

        interceptor.respondWithError(errorJson, 400)
        orderRestClient.updateOrderStatus(originalOrder, siteModel, CoreOrderStatus.REFUNDED.value)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.UPDATED_ORDER_STATUS, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteOrderPayload
        with(payload) {
            // Expecting a 'invalid id' error from the server
            assertNotNull(error)
            assertEquals(OrderErrorType.INVALID_ID, error.type)
        }
    }

    @Test
    fun testOrderNotesFetchSuccess() {
        interceptor.respondWith("wc-order-notes-response-success.json")
        orderRestClient.fetchOrderNotes(
                WCOrderModel().apply {
                    localSiteId = 5
                    id = 8
                    remoteOrderId = 88
                }, siteModel
        )

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDER_NOTES, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrderNotesResponsePayload
        assertNull(payload.error)
        assertEquals(8, payload.notes.size)

        // Verify basic order fields and private, system note
        with(payload.notes[0]) {
            assertEquals(1942, remoteNoteId)
            assertEquals("2018-04-27T20:48:10Z", dateCreated)
            assertEquals(5, localSiteId)
            assertEquals(8, localOrderId)
            assertEquals(
                    "Email queued: Poster Purchase Follow-Up scheduled " +
                            "on Poster Purchase Follow-Up<br/>Trigger: Poster Purchase Follow-Up", note
            )
            assertFalse(isCustomerNote)
            assertTrue(isSystemNote)
        }

        // Verify private user-created note
        with(payload.notes[6]) {
            assertEquals("Interesting order!", note)
            assertFalse(isCustomerNote)
            assertFalse(isSystemNote)
        }

        // Verify customer-facing note
        with(payload.notes[7]) {
            assertEquals("Shipping soon!", note)
            assertTrue(isCustomerNote)
            assertFalse(isSystemNote)
        }
    }

    @Test
    fun testOrderNotesFetchError() {
        interceptor.respondWithError("wc-order-notes-response-failure-invalid-id.json", 404)
        orderRestClient.fetchOrderNotes(
                WCOrderModel().apply {
                    localSiteId = 5
                    id = 8
                    remoteOrderId = 88
                }, siteModel
        )

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDER_NOTES, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrderNotesResponsePayload
        with(payload) {
            // Expecting a 'invalid id' error from the server
            assertNotNull(error)
            assertEquals(OrderErrorType.INVALID_ID, error.type)
        }
    }

    @Test
    fun testOrderNotePostSuccess() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        val originalNote = WCOrderNoteModel().apply {
            localOrderId = 5
            localSiteId = siteModel.id
            note = "Test rest note"
            isCustomerNote = true
        }

        interceptor.respondWith("wc-order-note-post-response-success.json")
        orderRestClient.postOrderNote(orderModel, siteModel, originalNote)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.POSTED_ORDER_NOTE, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteOrderNotePayload
        with(payload) {
            assertNull(error)
            assertEquals(originalNote.note, note.note)
            assertEquals(originalNote.isCustomerNote, note.isCustomerNote)
            assertFalse(note.isSystemNote) // Any note created from the app should be flagged as user-created
            assertEquals(originalNote.localOrderId, note.localOrderId)
            assertEquals(originalNote.localSiteId, note.localSiteId)
        }
    }

    @Test
    fun testOrderNotePostError() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        val originalNote = WCOrderNoteModel().apply {
            localOrderId = 5
            localSiteId = siteModel.id
            note = "Test rest note"
            isCustomerNote = true
        }

        val errorJson = JsonObject().apply {
            addProperty("error", "woocommerce_rest_shop_order_invalid_id")
            addProperty("message", "Invalid ID.")
        }

        interceptor.respondWithError(errorJson, 400)
        orderRestClient.postOrderNote(orderModel, siteModel, originalNote)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.POSTED_ORDER_NOTE, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteOrderNotePayload
        with(payload) {
            // Expecting a 'invalid id' error from the server
            assertNotNull(error)
            assertEquals(OrderErrorType.INVALID_ID, error.type)
        }
    }

    @Test
    fun testHasAnyOrders() {
        interceptor.respondWith("wc-has-orders-response-success.json")
        orderRestClient.fetchHasOrders(siteModel, filterByStatus = null)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_HAS_ORDERS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchHasOrdersResponsePayload
        assertNull(payload.error)
        assertTrue(payload.hasOrders)
        assertNull(payload.statusFilter)
    }

    @Test
    fun testOrderStatusOptionsFetchSuccess() {
        interceptor.respondWith("wc-fetch-order-status-options-success.json")
        orderRestClient.fetchOrderStatusOptions(siteModel)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDER_STATUS_OPTIONS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrderStatusOptionsResponsePayload
        assertNull(payload.error)
        assertEquals(8, payload.labels.size)

        with(payload.labels[0]) {
            assertEquals("pending", statusKey)
            assertEquals("Pending payment", label)
        }
    }

    @Test
    fun testOrderShipmentTrackingsFetchSuccess() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        interceptor.respondWith("wc-order-shipment-trackings-success.json")
        orderRestClient.fetchOrderShipmentTrackings(siteModel, orderModel)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDER_SHIPMENT_TRACKINGS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrderShipmentTrackingsResponsePayload
        assertNull(payload.error)
        assertEquals(2, payload.trackings.size)

        with(payload.trackings[0]) {
            assertEquals(remoteTrackingId, "19b28e4151dc5b4ae1c27294ede241f9")
            assertEquals(trackingProvider, "USPS")
            assertEquals(
                    trackingLink,
                    "https://tools.usps.com/go/TrackConfirmAction_input?qtc_tLabels1=11122233344466666")
            assertEquals(trackingNumber, "11122233344466666")
            assertEquals(dateShipped, "2019-02-19")
        }
    }

    @Test
    fun testAddOrderShipmentTrackingSuccess() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        val trackingModel = WCOrderShipmentTrackingModel().apply {
            trackingProvider = "TNT Express (consignment)"
            trackingNumber = "123456"
            dateShipped = "2019-04-18"
        }
        interceptor.respondWith("wc-post-order-shipment-tracking-success.json")
        orderRestClient.addOrderShipmentTrackingForOrder(siteModel, orderModel, trackingModel, isCustomProvider = false)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.ADDED_ORDER_SHIPMENT_TRACKING, lastAction!!.type)
        val payload = lastAction!!.payload as AddOrderShipmentTrackingResponsePayload
        assertNull(payload.error)
        assertNotNull(payload.tracking)

        with(payload.tracking!!) {
            assertEquals(remoteTrackingId, "95bb641d79d7c6974001d6a03fbdabc0")
            assertEquals(trackingNumber, "123456")
            assertEquals(trackingProvider, "TNT Express (consignment)")
            assertEquals(trackingLink, "http://www.tnt.com/webtracker/tracking.do?requestType=GEN&searchType=" +
                    "CON&respLang=en&respCountry=GENERIC&sourceID=1&sourceCountry=ww&cons=123456&navigation=1&g" +
                    "\nenericSiteIdent=")
            assertEquals(dateShipped, "2019-04-18")
        }
    }

    @Test
    fun testAddOrderShipmentTrackingCustomProviderSuccess() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        val trackingModel = WCOrderShipmentTrackingModel().apply {
            trackingProvider = "Amanda Test Provider"
            trackingNumber = "123456"
            trackingLink = "https://www.google.com"
            dateShipped = "2019-04-19"
        }
        interceptor.respondWith("wc-post-order-shipment-tracking-custom-success.json")
        orderRestClient.addOrderShipmentTrackingForOrder(siteModel, orderModel, trackingModel, isCustomProvider = true)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.ADDED_ORDER_SHIPMENT_TRACKING, lastAction!!.type)
        val payload = lastAction!!.payload as AddOrderShipmentTrackingResponsePayload
        assertNull(payload.error)
        assertNotNull(payload.tracking)

        with(payload.tracking!!) {
            assertEquals(remoteTrackingId, "ecfb139dcc180833b8dbe92e438913fc")
            assertEquals(trackingNumber, "123456")
            assertEquals(trackingProvider, "Amanda Test Provider")
            assertEquals(trackingLink, "https://www.google.com")
            assertEquals(dateShipped, "2019-04-19")
        }
    }

    @Test
    fun testDeleteOrderShipmentTrackingSuccess() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        val trackingModel = WCOrderShipmentTrackingModel().apply {
            remoteTrackingId = "95bb641d79d7c6974001d6a03fbdabc0"
        }

        interceptor.respondWith("wc-delete-order-shipment-tracking-success.json")
        orderRestClient.deleteShipmentTrackingForOrder(siteModel, orderModel, trackingModel.remoteTrackingId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.DELETED_ORDER_SHIPMENT_TRACKING, lastAction!!.type)
        val payload = lastAction!!.payload as DeleteOrderShipmentTrackingResponsePayload
        assertNull(payload.error)
        assertNotNull(payload.tracking)

        with(payload.tracking!!) {
            assertEquals(remoteTrackingId, "95bb641d79d7c6974001d6a03fbdabc0")
        }
    }

    @Test
    fun testOrderShipmentProvidersFetchSuccess() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        interceptor.respondWith("wc-order-shipment-providers-success.json")
        orderRestClient.fetchOrderShipmentProviders(siteModel, orderModel)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDER_SHIPMENT_PROVIDERS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrderShipmentProvidersResponsePayload
        assertNull(payload.error)
        assertEquals(54, payload.providers.size)

        with(payload.providers[0]) {
            assertEquals(localSiteId, siteModel.id)
            assertEquals("Australia", country)
            assertEquals("Australia Post", carrierName)
            assertEquals("http://auspost.com.au/track/track.html?id=%1\$s", carrierLink)
        }
    }

    /**
     * We had a user with a site that returned simply "failed" without an error when requesting the
     * shipment provider list, resulting in a crash when parsing the response. This tests that
     * situation and ensures we dispatch an error
     */
    @Test
    fun testOrderShipmentProvidersFetchFailed() {
        val orderModel = WCOrderModel(5).apply { localSiteId = siteModel.id }
        interceptor.respondWith("wc-order-shipment-providers-failed.json")
        orderRestClient.fetchOrderShipmentProviders(siteModel, orderModel)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), MILLISECONDS))

        assertEquals(WCOrderAction.FETCHED_ORDER_SHIPMENT_PROVIDERS, lastAction!!.type)
        val payload = lastAction!!.payload as FetchOrderShipmentProvidersResponsePayload
        assertNotNull(payload.error)
        assertEquals(payload.error.type, OrderErrorType.INVALID_RESPONSE)
    }

    @Suppress("unused")
    @Subscribe
    fun onAction(action: Action<*>) {
        lastAction = action
        countDownLatch.countDown()
    }
}
