package rw.rra.roomiq.booking.domain.type;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSqlTstzRangeType implements UserType<PGobject> {
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<PGobject> returnedClass() {
        return PGobject.class;
    }

    @Override
    public PGobject nullSafeGet(ResultSet resultSet, int position,
                                SharedSessionContractImplementor session, Object owner) throws SQLException {
        Object value = resultSet.getObject(position);
        if (value == null) {
            return null;
        }
        return value instanceof PGobject postgresObject ? postgresObject : rangeObject(value.toString());
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, PGobject value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value, Types.OTHER);
        }
    }

    @Override
    public PGobject deepCopy(PGobject value) {
        return value == null ? null : rangeObject(value.getValue());
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(PGobject value) {
        return deepCopy(value);
    }

    @Override
    public PGobject assemble(Serializable cached, Object owner) {
        return cached instanceof PGobject value ? deepCopy(value) : null;
    }

    private PGobject rangeObject(String value) {
        try {
            PGobject range = new PGobject();
            range.setType("tstzrange");
            range.setValue(value);
            return range;
        } catch (SQLException exception) {
            throw new IllegalArgumentException("Invalid PostgreSQL timestamp range", exception);
        }
    }
}
