/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.migrate;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.SQLDatabaseConnector;
import xyz.kyngs.librelogin.common.database.AuthenticUser;

/** Base abstract class for SQL-based migration read providers. */
public abstract class SQLMigrateReadProvider extends MigrateReadProvider {

    /**
     * Algorithm stored for imported accounts whose password cannot be verified (unsupported hash,
     * no password, unreadable row). No crypto provider is registered under it, so the account stays
     * registered and nobody can {@code /register} the name and inherit its UUID; logging in reports
     * {@code error.password-corrupted} until an administrator runs {@code /librelogin user
     * setpassword}.
     */
    public static final String LOCKED_ALGORITHM = "UNSUPPORTED";

    private static final int LISTED_NAMES = 20;
    private static final int HASH_COLUMN_LENGTH = 255;

    protected final String tableName;
    protected final Logger logger;
    protected final SQLDatabaseConnector connector;

    /**
     * Constructs a new SQL migration read provider.
     *
     * @param tableName the legacy table name to read users from
     * @param logger the logger instance
     * @param connector the SQL database connector
     */
    public SQLMigrateReadProvider(String tableName, Logger logger, SQLDatabaseConnector connector) {
        this.tableName = tableName;
        this.logger = logger;
        this.connector = connector;
    }

    /**
     * Returns the table name safely quoted for standard SQL queries.
     *
     * @return the escaped SQL table identifier
     */
    /**
     * Creates the password of a locked account (see {@link #LOCKED_ALGORITHM}).
     *
     * @param original the original stored value, kept for reference when it fits the column
     * @return the locked password
     */
    protected static HashedPassword locked(@Nullable String original) {
        var hash = original == null || original.length() > HASH_COLUMN_LENGTH ? "" : original;
        return new HashedPassword(hash, null, LOCKED_ALGORITHM);
    }

    /**
     * Creates a locked account for a row whose other data could not be read.
     *
     * @param uuid the account UUID
     * @param name the account name
     * @return the locked account, with no other data
     */
    protected static User lockedUser(UUID uuid, String name) {
        return new AuthenticUser(
                uuid, null, locked(null), name, null, null, null, null, null, null, null);
    }

    /**
     * Logs the accounts imported locked, so the administrator can unlock them.
     *
     * @param source the name of the migrated plugin
     * @param names the names of the locked accounts
     */
    protected void warnLocked(String source, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        logger.warn(
                names.size()
                        + " "
                        + source
                        + " account(s) were imported locked because their password cannot be"
                        + " verified: "
                        + listNames(names)
                        + ". They cannot log in, and nobody else can register their names, until an"
                        + " administrator runs /librelogin user setpassword <name> <password>.");
    }

    /**
     * Logs how many source rows were skipped because they hold no account to migrate.
     *
     * @param source the name of the migrated plugin
     * @param count the number of skipped rows
     * @param reason why the rows were skipped
     */
    protected void warnSkipped(String source, int count, String reason) {
        if (count > 0) {
            logger.warn(count + " " + source + " row(s) were skipped: " + reason + ".");
        }
    }

    /**
     * Joins the first names of a list for log and error messages.
     *
     * @param names the names
     * @return up to 20 comma-separated names, followed by "..." when more exist
     */
    protected static String listNames(List<String> names) {
        return String.join(", ", names.subList(0, Math.min(LISTED_NAMES, names.size())))
                + (names.size() > LISTED_NAMES ? ", ..." : "");
    }

    protected String getEscapedTableName() {
        if (tableName == null) return "users";
        if (tableName.startsWith("`") || tableName.startsWith("\"") || tableName.startsWith("[")) {
            return tableName;
        }
        if (connector
                instanceof
                xyz.kyngs.librelogin.api.database.connector.PostgreSQLDatabaseConnector) {
            return "\"" + tableName.replace("\"", "\"\"") + "\"";
        }
        return "`" + tableName.replace("`", "``") + "`";
    }
}
