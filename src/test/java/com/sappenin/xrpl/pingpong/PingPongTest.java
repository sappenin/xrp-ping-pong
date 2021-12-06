package com.sappenin.xrpl.pingpong;


import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.TextParseException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;

class PingPongTest {

  @Test
  void testAllS2HostsMainnet()
    throws ExecutionException, InterruptedException, TextParseException, UnknownHostException {

    List<String> expectedIps = Lists.newArrayList(
      "34.214.118.3",
      "34.217.214.6",
      "52.13.55.64",
      "34.219.203.68",
      "34.219.9.70",
      "54.188.163.79",
      "54.212.59.81",
      "44.235.186.96",
      "54.188.58.162",
      "18.236.148.191",
      "54.188.58.162",
      "54.184.96.167",
      "54.148.68.202",
      "34.220.65.204",
      "34.211.159.205",
      "52.10.167.208",
      "18.236.246.210",
      "54.200.184.214",
      "34.222.52.244"
    );

    List<InetAddress> result = PingPong.getAllS2HostsInParallel(Network.MAINNET);

    // Verify each IP is in the list above...
    result.stream()
      .map(InetAddress::getHostAddress)
      .forEach(addr -> assertThat(expectedIps).contains(addr));

    assertThat(result).hasSize(18);
  }

  @Test
  void testAllS2HostsTestnet()
    throws ExecutionException, InterruptedException, TextParseException, UnknownHostException {

    List<String> expectedIps = Lists.newArrayList(
      "34.210.87.206"
    );

    List<InetAddress> result = PingPong.getAllS2HostsInParallel(Network.TESTNET);

    // Verify each IP is in the list above...
    result.stream()
      .map(InetAddress::getHostAddress)
      .forEach(addr -> assertThat(expectedIps).contains(addr));

    assertThat(result).hasSize(1);
  }
}