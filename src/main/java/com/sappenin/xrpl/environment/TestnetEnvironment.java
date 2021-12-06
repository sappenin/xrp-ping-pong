package com.sappenin.xrpl.environment;

import okhttp3.HttpUrl;
import org.xrpl.xrpl4j.client.XrplClient;

/**
 * XRPL testnet environment.
 */
public class TestnetEnvironment implements XrplEnvironment {
//
//  private final FaucetClient faucetClient =
//    FaucetClient.construct(HttpUrl.parse("https://faucet.altnet.rippletest.net"));

  private final XrplClient xrplClient = new XrplClient(HttpUrl.parse("https://s.altnet.rippletest.net:51234"));

  @Override
  public XrplClient getXrplClient() {
    return xrplClient;
  }

  @Override
  public XrplClient getXrplClient(String ipAddress) {
    return new XrplClient(HttpUrl.parse(ipAddress));
  }

//  @Override
//  public void fundAccount(Address classicAddress) {
//    faucetClient.fundAccount(FundAccountRequest.of(classicAddress));
//  }

}
