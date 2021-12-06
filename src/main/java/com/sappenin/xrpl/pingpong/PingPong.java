package com.sappenin.xrpl.pingpong;

import static org.awaitility.Awaitility.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.sappenin.xrpl.environment.MainnetEnvironment;
import com.sappenin.xrpl.environment.TestnetEnvironment;
import com.sappenin.xrpl.environment.XrplEnvironment;
import org.awaitility.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.lookup.LookupSession;
import org.xbill.DNS.lookup.LookupSession.LookupSessionBuilder;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.client.XrplResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.common.LedgerSpecifier;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Hash256;
import org.xrpl.xrpl4j.model.transactions.Memo;
import org.xrpl.xrpl4j.model.transactions.MemoWrapper;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.Transaction;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;
import org.xrpl.xrpl4j.wallet.DefaultWalletFactory;
import org.xrpl.xrpl4j.wallet.Wallet;
import org.xrpl.xrpl4j.wallet.WalletFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * PingPong is meant to be a test-harness to ensure that various nodes in rippled cluster are configured properly (i.e.,
 * they're not running in reporting mode, and can handle transaction submission). Especially for IP-LoadBalanced
 * clusters like s1.ripple.com and s2.ripple.com, this tool ensures that every node in a cluster is properly configured
 * by ensuring that transactions can be ping-ponged between two accounts but using various IP addressed returned by the
 * load-balancer.
 */
@Command(name = "pingpong", mixinStandardHelpOptions = true, version = "pingpong 1.0", description = "Sends AMOUNT drops between two accounts")
public class PingPong implements Callable<Integer> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PingPong.class);
  private static final Duration POLL_INTERVAL = Duration.ONE_HUNDRED_MILLISECONDS;
  public static final String SUCCESS_STATUS = "tesSUCCESS";

  private XrplEnvironment xrplEnvironment;
  private Iterable<InetAddress> cyclableResults;

  @Option(names = {"-s1",
    "--secret1"}, paramLabel = "SECRET1", description = "Base58-encoded secret for the first account", required = true)
  private String account1Secret;

  @Option(names = {"-s2",
    "--secret2"}, paramLabel = "SECRET2", description = "Base58-encoded secret for the second account", required = true)
  private String account2Secret;

  @Option(names = {"-f",
    "--fee"}, paramLabel = "FEE", defaultValue = "25", description = "The fee to use when sending a payment")
  private BigInteger fee;

  @Option(names = {"-n",
    "--network"}, paramLabel = "NETWORK", defaultValue = Constants.TESTNET, completionCandidates = KnownNetworks.class, description = "The network to send on")
  // TODO: Make a TypeConverter for Network.
  private String network;

  @Option(names = {"-s",
    "--submission-hosts"}, paramLabel = "HOSTS", defaultValue = "s1.livenet.ripple.com,s2.livenet.ripple.com", description = "The hosts to lookup DNS round-robin values from")
  private String submissionHosts;

  @Parameters(index = "0", paramLabel = "AMOUNT", description = "Amount of XRP Drops to send")
  private BigInteger amountDrops;

  @Override
  public Integer call() throws Exception { // your business logic goes here...
    LOGGER.warn("Using Network: {}", this.network);
    switch (this.network) {
      case Constants.MAINNET: {
        this.xrplEnvironment = new MainnetEnvironment();
      }
      case Constants.TESTNET:
      default: {
        this.xrplEnvironment = new TestnetEnvironment();
      }
    }

    WalletFactory walletFactory = DefaultWalletFactory.getInstance();
    Wallet wallet1 = walletFactory.fromSeed(account1Secret, isTest(this.network));
    Wallet wallet2 = walletFactory.fromSeed(account2Secret, isTest(this.network));

    // Core Wallets.
//    Seed seed1 = new Seed(UnsignedByteArray.of(Base58.decode(account1Secret)));
//    Wallet wallet1 = walletFactory.fromSeed(seed1);
//    Seed seed2 = new Seed(UnsignedByteArray.of(Base58.decode(account2Secret)));
//    Wallet wallet2 = walletFactory.fromSeed(seed2);

    // TODO: Get Tickets...

    int numPayments = 0;
    this.populateIpAddresses();
    Iterator<InetAddress> hostAddressIterator = this.cyclableResults.iterator();
    while (true) {
      final Wallet sender;
      final Wallet receiver;
      if (numPayments % 2 == 0) {
        sender = wallet1;
        receiver = wallet2;
      } else {
        sender = wallet2;
        receiver = wallet1;
      }

      String hostAddress = "http://" + hostAddressIterator.next().getHostAddress() + ":51234";
      LOGGER.info("Submission Host Address: {}", hostAddress);

      XrplClient xrplClient = this.xrplEnvironment.getXrplClient(hostAddress);
      FeeResult feeResult = xrplClient.fee();
      LOGGER.info("Current Fee: {}", feeResult);
      AccountInfoResult accountInfo = this.scanForResult(
        () -> this.getValidatedAccountInfo(xrplClient, sender.classicAddress()));
      XrpCurrencyAmount amount = XrpCurrencyAmount.ofDrops(1);
      Payment payment = Payment.builder().account(sender.classicAddress()).fee(feeResult.drops().openLedgerFee())
        .sequence(accountInfo.accountData().sequence()).destination(receiver.classicAddress()).amount(amount)
        .addMemos(MemoWrapper.builder().memo(Memo.builder()
//              .memoFormat(BaseEncoding.base16().encode("text/plain".getBytes(StandardCharsets.UTF_8)))
          //.memoType("text/plain")
          .memoData(BaseEncoding.base16().encode("xrp-ping-pong".getBytes(StandardCharsets.UTF_8)))
          //.memoData("xrp-ping-pong")
          .build()).build()).signingPublicKey(sender.publicKey()).build();

      SubmitResult<Payment> result = xrplClient.submit(sender, payment);
      assert result.result().equals(SUCCESS_STATUS);
      LOGGER.info("Payment from {} to {} (network={}, status={}, hash={})", wallet1.classicAddress(),
        wallet2.classicAddress(), this.network, result.result(), result.transactionResult().hash());

      TransactionResult<Payment> validatedPayment = this.scanForResult(
        () -> this.getValidatedTransaction(xrplClient, result.transactionResult().hash(), Payment.class));
      LOGGER.info("Validated Payment: {}", validatedPayment);

      assert validatedPayment.metadata().get().deliveredAmount().get().equals(amount);
      assert validatedPayment.metadata().get().transactionResult().equals(SUCCESS_STATUS);

      numPayments++;

      // every 100 payments, cycle the DNS queries...
      if (numPayments % 100 == 0) {
        this.populateIpAddresses();
      }

      LOGGER.info("Sleeping for 30 seconds...");
      Thread.sleep(30000);
    }
  }

  // this example implements Callable, so parsing, error handling and handling user
  // requests for usage help or version help can be done with one line of code.
  public static void main(String... args) {
    java.security.Security.setProperty("networkaddress.cache.ttl", "0");
    int exitCode = new CommandLine(new PingPong()).setColorScheme(Help.defaultColorScheme(Ansi.AUTO)).execute(args);
    System.exit(exitCode);
  }


  /**
   * Adapter class that presents the Minecraft player database as an {@code Iterable<String>}.
   */
  static class KnownNetworks implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
      return Lists.newArrayList(Network.DEVNET.value(), Network.TESTNET.value(), Network.MAINNET.value()).iterator();
    }
  }

  private Iterable<String> parseSubmissionHosts(final String submissionHosts) {
    return Arrays.asList(submissionHosts.split(","));
  }

  public static CompletableFuture<List<InetAddress>> dnsLookup(Network network)
    throws TextParseException, UnknownHostException {

    LookupSessionBuilder lookupSessionBuilder = LookupSession.builder().clearCaches();

    final Name aLookup;
    if (network == Network.MAINNET) {
      aLookup = Name.fromString("s2.livenet.ripple.com.");
      lookupSessionBuilder.resolver(new SimpleResolver("ns-1907.awsdns-46.co.uk"));
    } else {
      aLookup = Name.fromString("s.altnet.rippletest.net.");
      lookupSessionBuilder.resolver(new SimpleResolver("8.8.8.8"));
    }

    return lookupSessionBuilder.build().lookupAsync(aLookup, Type.A).thenApplyAsync(
      (answers) -> answers.getRecords().stream().map($ -> (ARecord) $).map(ARecord::getAddress)
        .collect(Collectors.toList())).whenComplete((answers, ex) -> {
      if (ex != null) {
        throw new RuntimeException(ex.getMessage(), ex);
      }
    }).toCompletableFuture();
  }

  public static List<InetAddress> getAllS2HostsInParallel(Network network)
    throws TextParseException, ExecutionException, InterruptedException, UnknownHostException {

    List<CompletableFuture<List<InetAddress>>> futures = Lists.newArrayList();
    if (network == Network.MAINNET) {
      for (int i = 0; i < 200; i++) {
        futures.add(dnsLookup(network));
      }
    } else {
      for (int i = 0; i < 10; i++) {
        futures.add(dnsLookup(network));
      }
    }

    CompletableFuture<Void> aggregatedFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    List<InetAddress> allResults = aggregatedFuture.thenApply(
        $ -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
      .get() // This will be a List of Sets, that needs to be flattened.
      .stream().flatMap(Collection::stream).collect(Collectors.toList());

    Set<InetAddress> deduped = Sets.newConcurrentHashSet();
    deduped.addAll(allResults);

    List<InetAddress> ipAddresses = deduped.stream().toList();
    LOGGER.info("rippled IP addresses: {}", ipAddresses);
    return ipAddresses;
  }

  private void getTickets(final Wallet wallet, int numTickets) {

    // TODO: See issue in xrpl4j

  }

  protected <T extends XrplResult> T scanForResult(Supplier<T> resultSupplier) {
    Objects.requireNonNull(resultSupplier);
    return given().pollInterval(POLL_INTERVAL).atMost(Duration.TEN_SECONDS).ignoreException(RuntimeException.class)
      .await().until(resultSupplier::get, is(notNullValue()));
  }

  protected AccountInfoResult getValidatedAccountInfo(XrplClient xrplClient, Address classicAddress) {
    try {
      AccountInfoRequestParams params = AccountInfoRequestParams.builder().account(classicAddress)
        .ledgerSpecifier(LedgerSpecifier.VALIDATED).build();
      return xrplClient.accountInfo(params);
    } catch (Exception e) {
      LOGGER.info("Exception: {}", e.getMessage());
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private boolean isTest(String network) {
    return !network.contains("Main");
  }

  protected <T extends Transaction> TransactionResult<T> getValidatedTransaction(XrplClient xrplClient,
    Hash256 transactionHash, Class<T> transactionType) {
    try {
      TransactionResult<T> transaction = xrplClient.transaction(TransactionRequestParams.of(transactionHash),
        transactionType);
      return transaction.validated() ? transaction : null;
    } catch (JsonRpcClientErrorException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private void populateIpAddresses()
    throws UnknownHostException, ExecutionException, InterruptedException, TextParseException {

    final Network network;
    if (this.network.contains("Main")) {
      network = Network.MAINNET;
    } else {
      network = Network.TESTNET;
    }

    final List<InetAddress> results = this.getAllS2HostsInParallel(network);
    this.cyclableResults = Iterables.cycle(results);
  }
}