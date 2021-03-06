package org.whispersystems.textsecuregcm.workers;

import net.sourceforge.argparse4j.inf.Namespace;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.storage.Accounts;
import org.whispersystems.textsecuregcm.storage.Keys;
import org.whispersystems.textsecuregcm.storage.PendingAccounts;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.jdbi.args.OptionalArgumentFactory;
import io.dropwizard.setup.Bootstrap;


public class VacuumCommand extends ConfiguredCommand<WhisperServerConfiguration> {

  private final Logger logger = LoggerFactory.getLogger(DirectoryCommand.class);

  public VacuumCommand() {
    super("vacuum", "Vacuum Postgres Tables");
  }

  @Override
  protected void run(Bootstrap<WhisperServerConfiguration> bootstrap,
                     Namespace namespace,
                     WhisperServerConfiguration config)
      throws Exception
  {
    DataSourceFactory dbConfig = config.getDataSourceFactory();
    DBI               dbi      = new DBI(dbConfig.getUrl(), dbConfig.getUser(), dbConfig.getPassword());

    dbi.registerArgumentFactory(new OptionalArgumentFactory(dbConfig.getDriverClass()));
    dbi.registerContainerFactory(new ImmutableListContainerFactory());
    dbi.registerContainerFactory(new ImmutableSetContainerFactory());
    dbi.registerContainerFactory(new OptionalContainerFactory());

    Accounts        accounts        = dbi.onDemand(Accounts.class       );
    Keys            keys            = dbi.onDemand(Keys.class           );
    PendingAccounts pendingAccounts = dbi.onDemand(PendingAccounts.class);

    logger.warn("Vacuuming accounts...");
    accounts.vacuum();

    logger.warn("Vacuuming pending_accounts...");
    pendingAccounts.vacuum();

    logger.warn("Vacuuming keys...");
    keys.vacuum();

    Thread.sleep(3000);
    System.exit(0);
  }
}
