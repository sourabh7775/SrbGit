package com.angelbroking.smartapi;

import com.angelbroking.smartapi.http.SessionExpiryHook;
import com.angelbroking.smartapi.http.SmartAPIRequestHandler;
import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
import com.angelbroking.smartapi.models.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmartConnect {

    private static final Logger log = LoggerFactory.getLogger(SmartConnect.class);
    private static final Gson gson = new Gson();

    private final String apiKey;
    private String accessToken;
    private String refreshToken;
    private String feedToken;
    private String userId;
    private SessionExpiryHook sessionExpiryHook;

    private final Routes routes = new Routes();
    private final SmartAPIRequestHandler requestHandler;

    public SmartConnect(String apiKey) {
        this.apiKey = apiKey;
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.requestHandler = new SmartAPIRequestHandler(client);
        this.requestHandler.setApiKey(apiKey);
    }

    public void setSessionExpiryHook(SessionExpiryHook hook) {
        this.sessionExpiryHook = hook;
    }

    public User generateSession(String clientCode, String password, String totp)
            throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("clientcode", clientCode);
        params.put("password", password);
        params.put("totp", totp);

        JSONObject response = requestHandler.postLoginRequest(routes.getLoginUrl(), params);
        JSONObject data = response.getJSONObject("data");

        User user = gson.fromJson(data.toString(), User.class);
        this.accessToken = user.accessToken;
        this.refreshToken = user.refreshToken;
        this.feedToken = user.feedToken;
        this.userId = clientCode;
        requestHandler.setAccessToken(accessToken);
        return user;
    }

    public TokenSet generateToken(String refreshToken) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("refreshToken", refreshToken);
        JSONObject response = requestHandler.postRequest(routes.get("api.token"), params, apiKey, accessToken);
        JSONObject data = response.getJSONObject("data");
        TokenSet tokenSet = gson.fromJson(data.toString(), TokenSet.class);
        this.accessToken = tokenSet.jwtToken;
        this.refreshToken = tokenSet.refreshToken;
        this.feedToken = tokenSet.feedToken;
        requestHandler.setAccessToken(accessToken);
        return tokenSet;
    }

    public boolean terminateSession(String clientCode) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("clientcode", clientCode);
        JSONObject response = requestHandler.postRequest(routes.get("api.user.logout"), params, apiKey, accessToken);
        return response.optBoolean("status", false);
    }

    public Profile getProfile() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.user.profile"), apiKey, accessToken);
        return gson.fromJson(response.getJSONObject("data").toString(), Profile.class);
    }

    public JSONObject placeOrder(OrderParams orderParams) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("variety", orderParams.variety);
        params.put("tradingsymbol", orderParams.tradingsymbol);
        params.put("symboltoken", orderParams.symboltoken);
        params.put("transactiontype", orderParams.transactiontype);
        params.put("exchange", orderParams.exchange);
        params.put("ordertype", orderParams.ordertype);
        params.put("producttype", orderParams.producttype);
        params.put("duration", orderParams.duration);
        params.put("price", orderParams.price);
        params.put("squareoff", orderParams.squareoff);
        params.put("stoploss", orderParams.stoploss);
        params.put("quantity", orderParams.quantity);
        params.put("triggerprice", orderParams.triggerprice);
        if (orderParams.disclosedquantity != null) params.put("disclosedquantity", orderParams.disclosedquantity);
        if (orderParams.ordertag != null) params.put("ordertag", orderParams.ordertag);

        JSONObject response = requestHandler.postRequest(routes.get("api.order.place"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject modifyOrder(OrderParams orderParams, String orderId) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("variety", orderParams.variety);
        params.put("orderid", orderId);
        params.put("ordertype", orderParams.ordertype);
        params.put("producttype", orderParams.producttype);
        params.put("duration", orderParams.duration);
        params.put("price", orderParams.price);
        params.put("quantity", orderParams.quantity);
        params.put("tradingsymbol", orderParams.tradingsymbol);
        params.put("symboltoken", orderParams.symboltoken);
        params.put("exchange", orderParams.exchange);
        params.put("triggerprice", orderParams.triggerprice);

        JSONObject response = requestHandler.postRequest(routes.get("api.order.modify"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject cancelOrder(String orderId, String variety) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("orderid", orderId);
        params.put("variety", variety);
        JSONObject response = requestHandler.postRequest(routes.get("api.order.cancel"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public List<Order> getOrderBook() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.order.book"), apiKey, accessToken);
        JSONArray data = response.optJSONArray("data");
        if (data == null) return List.of();
        return gson.fromJson(data.toString(), new TypeToken<List<Order>>(){}.getType());
    }

    public List<Trade> getTradeBook() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.order.trade.book"), apiKey, accessToken);
        JSONArray data = response.optJSONArray("data");
        if (data == null) return List.of();
        return gson.fromJson(data.toString(), new TypeToken<List<Trade>>(){}.getType());
    }

    public JSONObject getRMSLimit() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.order.rms.data"), apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject getHolding() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.order.rms.holding"), apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject getAllHolding() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.order.rms.AllHolding"), apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject getPosition() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.order.rms.position"), apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject convertPosition(JSONObject params) throws SmartAPIException, IOException {
        JSONObject response = requestHandler.postRequest(routes.get("api.order.rms.position.convert"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject getLTP(String exchange, String tradingSymbol, String symbolToken)
            throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("exchange", exchange);
        params.put("tradingsymbol", tradingSymbol);
        params.put("symboltoken", symbolToken);
        JSONObject response = requestHandler.postRequest(routes.get("api.ltp.data"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject createRule(GttParams gttParams) throws SmartAPIException, IOException {
        JSONObject params = gson.fromJson(gson.toJson(gttParams), JSONObject.class);
        JSONObject response = requestHandler.postRequest(routes.get("api.gtt.create"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject modifyRule(GttParams gttParams, String id) throws SmartAPIException, IOException {
        JSONObject params = gson.fromJson(gson.toJson(gttParams), JSONObject.class);
        params.put("id", id);
        JSONObject response = requestHandler.postRequest(routes.get("api.gtt.modify"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public boolean cancelRule(String id, String symbolToken, String tradingSymbol, String exchange)
            throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("id", id);
        params.put("symboltoken", symbolToken);
        params.put("tradingsymbol", tradingSymbol);
        params.put("exchange", exchange);
        JSONObject response = requestHandler.postRequest(routes.get("api.gtt.cancel"), params, apiKey, accessToken);
        return response.optBoolean("status", false);
    }

    public JSONObject ruleDetails(String id) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("id", id);
        JSONObject response = requestHandler.postRequest(routes.get("api.gtt.details"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONArray ruleList(String status, int page, int count) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        JSONArray statusArr = new JSONArray();
        statusArr.put(status);
        params.put("status", statusArr);
        params.put("page", page);
        params.put("count", count);
        JSONObject response = requestHandler.postRequest(routes.get("api.gtt.list"), params, apiKey, accessToken);
        return response.optJSONArray("data");
    }

    public JSONArray candleData(JSONObject params) throws SmartAPIException, IOException {
        JSONObject response = requestHandler.postRequest(routes.get("api.candle.data"), params, apiKey, accessToken);
        Object data = response.opt("data");
        if (data instanceof JSONArray) return (JSONArray) data;
        if (data instanceof JSONObject) {
            JSONObject dataObj = (JSONObject) data;
            if (dataObj.has("fetched")) return dataObj.optJSONArray("fetched");
        }
        return new JSONArray();
    }

    public JSONObject searchScrip(String exchange, String searchScrip) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("exchange", exchange);
        params.put("searchscrip", searchScrip);
        JSONObject response = requestHandler.postRequest(routes.get("api.search.script.data"), params, apiKey, accessToken);
        return response;
    }

    public JSONObject getMarketData(String mode, JSONArray exchangeTokens) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("mode", mode);
        params.put("exchangeTokens", exchangeTokens);
        JSONObject response = requestHandler.postRequest(routes.get("api.market.data"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject marginBatch(JSONArray orders) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("orders", orders);
        JSONObject response = requestHandler.postRequest(routes.get("api.margin.batch"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject getIndividualOrder(String orderId) throws SmartAPIException, IOException {
        String url = routes.get("api.individual.order") + orderId;
        JSONObject response = requestHandler.getRequest(url, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject estimateCharges(JSONArray orders) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("orders", orders);
        JSONObject response = requestHandler.postRequest(routes.get("api.estimateCharges"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject optionGreek(String name, String expiryDate) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("name", name);
        params.put("expirydate", expiryDate);
        JSONObject response = requestHandler.postRequest(routes.get("api.optionGreek"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject gainersLosers(String datatype, String expirytype) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("datatype", datatype);
        params.put("expirytype", expirytype);
        JSONObject response = requestHandler.postRequest(routes.get("api.gainersLosers"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject putCallRatio() throws SmartAPIException, IOException {
        JSONObject response = requestHandler.getRequest(routes.get("api.putCallRatio"), apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public JSONObject oIBuildup(String expiryType, String dataType) throws SmartAPIException, IOException {
        JSONObject params = new JSONObject();
        params.put("expirytype", expiryType);
        params.put("datatype", dataType);
        JSONObject response = requestHandler.postRequest(routes.get("api.oIBuildup"), params, apiKey, accessToken);
        return response.optJSONObject("data");
    }

    public String getApiKey() { return apiKey; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getFeedToken() { return feedToken; }
    public String getUserId() { return userId; }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        requestHandler.setAccessToken(accessToken);
    }
}
