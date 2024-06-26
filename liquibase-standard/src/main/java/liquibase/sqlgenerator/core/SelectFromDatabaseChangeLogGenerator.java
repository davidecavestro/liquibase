package liquibase.sqlgenerator.core;

import liquibase.change.ColumnConfig;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.database.core.*;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.statement.core.SelectFromDatabaseChangeLogStatement;
import liquibase.util.StringUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class SelectFromDatabaseChangeLogGenerator extends AbstractSqlGenerator<SelectFromDatabaseChangeLogStatement> {

    @Override
    public ValidationErrors validate(SelectFromDatabaseChangeLogStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        ValidationErrors errors = new ValidationErrors();
        errors.checkRequiredField("columnToSelect", statement.getColumnsToSelect());

        return errors;
    }

    @Override
    public Sql[] generateSql(SelectFromDatabaseChangeLogStatement statement, final Database database, SqlGeneratorChain sqlGeneratorChain) {
        List<ColumnConfig> columnsToSelect = Arrays.asList(statement.getColumnsToSelect());
        // use LEGACY quoting since we're dealing with system objects
        ObjectQuotingStrategy currentStrategy = database.getObjectQuotingStrategy();
        database.setObjectQuotingStrategy(ObjectQuotingStrategy.LEGACY);
        try {
            String sql = "SELECT " + (database instanceof MSSQLDatabase && statement.getLimit() != null ? "TOP "+statement.getLimit()+" " : "") + StringUtil.join(columnsToSelect, ",", (StringUtil.StringUtilFormatter<ColumnConfig>) column -> {
                if ((column.getComputed() != null) && column.getComputed()) {
                    return column.getName();
                } else {
                    return database.escapeColumnName(null, null, null, column.getName());
                }
            }).toUpperCase() + " FROM " +
                    database.escapeTableName(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), database.getDatabaseChangeLogTableName());

            SelectFromDatabaseChangeLogStatement.WhereClause whereClause = statement.getWhereClause();
            if (whereClause != null) {
                sql += whereClause.generateSql(database);
            }

            if ((statement.getOrderByColumns() != null) && (statement.getOrderByColumns().length > 0)) {
                sql += " ORDER BY ";
                Iterator<String> orderBy = Arrays.asList(statement.getOrderByColumns()).iterator();

                while (orderBy.hasNext()) {
                    String orderColumn = orderBy.next();
                    String[] orderColumnData = orderColumn.split(" ");
                    sql += database.escapeColumnName(null, null, null, orderColumnData[0]);
                    if (orderColumnData.length == 2) {
                        sql += " ";
                        sql += orderColumnData[1].toUpperCase();
                    }
                    if (orderBy.hasNext()) {
                        sql += ", ";
                    }
                }
            }

            if (statement.getLimit() != null) {
                if (database instanceof OracleDatabase) {
                    if (whereClause == null) {
                        sql += " WHERE ROWNUM="+statement.getLimit();
                    } else {
                        sql += " AND ROWNUM="+statement.getLimit();
                    }
                } else if ((database instanceof MySQLDatabase) || (database instanceof PostgresDatabase)) {
                    sql += " LIMIT "+statement.getLimit();
                } else if (database instanceof AbstractDb2Database) {
                    sql += " FETCH FIRST "+statement.getLimit()+" ROWS ONLY";
                }
            }

            return new Sql[]{
                    new UnparsedSql(sql)
            };
        } finally {
            database.setObjectQuotingStrategy(currentStrategy);
        }
    }
}
