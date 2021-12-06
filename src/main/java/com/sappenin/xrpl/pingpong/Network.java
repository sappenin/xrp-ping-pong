package com.sappenin.xrpl.pingpong;

/**
 * Holds an indentifier for a particular XRPL network (e.g., Mainnet, Testnet, Devnet, sidechain, etc).
 */
public interface Network {

  Network DEVNET = Network.of(Constants.DEVNET);
  Network TESTNET = Network.of(Constants.TESTNET);
  Network MAINNET = Network.of(Constants.MAINNET);

  static Network of(final String value) {
    return new Network() {
      @Override
      public String value() {
        return value;
      }
    };
  }

  String value();
}
