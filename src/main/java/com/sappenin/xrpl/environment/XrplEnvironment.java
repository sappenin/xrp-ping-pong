package com.sappenin.xrpl.environment;

import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.transactions.Address;

/**
 * Abstraction for the XRP Ledger environment that the integration tests talk to. Provides access to resources need to
 * interact to the with the ledger.
 */
public interface XrplEnvironment {

  XrplClient getXrplClient();

  XrplClient getXrplClient(String ipAddress);

//  void fundAccount(Address classicAddress);

}
