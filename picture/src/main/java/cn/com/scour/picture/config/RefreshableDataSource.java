package cn.com.scour.picture.config;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@Slf4j
public class RefreshableDataSource implements DataSource, AutoCloseable {

    private final DataSourceFactory dataSourceFactory;

    private final AtomicReference<DataSource> delegate;

    public RefreshableDataSource(DataSourceFactory dataSourceFactory) throws SQLException {
        this.dataSourceFactory = Objects.requireNonNull(dataSourceFactory);
        this.delegate = new AtomicReference<>(dataSourceFactory.create());
    }

    public synchronized void refresh() throws SQLException {
        DataSource oldDataSource = delegate.get();
        DataSource newDataSource = dataSourceFactory.create();
        delegate.set(newDataSource);
        closeQuietly(oldDataSource);
        log.info("Refreshable datasource refreshed, newDataSourceClass={}", newDataSource.getClass().getName());
    }

    private DataSource current() {
        return delegate.get();
    }

    private void closeQuietly(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Close old datasource failed", e);
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return current().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return current().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return current().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        current().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        current().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return current().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return current().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return current().isWrapperFor(iface);
    }

    @Override
    public void close() {
        closeQuietly(delegate.get());
    }

    @FunctionalInterface
    public interface DataSourceFactory {

        DataSource create() throws SQLException;
    }
}
