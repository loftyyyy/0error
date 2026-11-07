package com.rho.backend.telemetry;

import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Bulk writer for raw keystrokes.
 *
 * <p>A single multi-row {@code INSERT ... ON CONFLICT DO NOTHING} makes a
 * retried batch cheap and keeps the unique {@code (session_id, seq)} index as
 * the only idempotency authority. {@code RETURNING seq} tells us exactly how
 * many rows were new, which the endpoint needs to answer 200 or 409.
 */
@Component
public class PostgresKeystrokeWriter implements KeystrokeWriter {

	private static final String COLUMNS = """
			session_id, seq, position, kind, expected_char, actual_char,
			is_correct, t_ms, display_ms, is_warmup
			""";

	private final DatabaseClient databaseClient;

	public PostgresKeystrokeWriter(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	@Override
	public Mono<Integer> insertIgnoringDuplicates(UUID sessionId, List<RawKeystroke> events) {
		if (events.isEmpty()) {
			return Mono.just(0);
		}

		StringBuilder sql = new StringBuilder("INSERT INTO keystroke_events (")
				.append(COLUMNS)
				.append(") VALUES ");

		for (int index = 0; index < events.size(); index++) {
			if (index > 0) {
				sql.append(", ");
			}
			sql.append("(:sessionId, :seq").append(index)
					.append(", :position").append(index)
					.append(", :kind").append(index)
					.append(", :expected").append(index)
					.append(", :actual").append(index)
					.append(", :correct").append(index)
					.append(", :t").append(index)
					.append(", :display").append(index)
					.append(", :warmup").append(index)
					.append(')');
		}

		sql.append(" ON CONFLICT (session_id, seq) DO NOTHING RETURNING seq");

		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
				.bind("sessionId", sessionId);

		for (int index = 0; index < events.size(); index++) {
			RawKeystroke event = events.get(index);
			spec = spec.bind("seq" + index, event.seq())
					.bind("position" + index, event.position())
					.bind("kind" + index, event.kind())
					.bind("correct" + index, event.correct())
					.bind("t" + index, event.t())
					.bind("warmup" + index, event.warmup());

			spec = bindNullable(spec, "expected" + index, event.expected(), String.class);
			spec = bindNullable(spec, "actual" + index, event.actual(), String.class);
			spec = bindNullable(spec, "display" + index, event.displayMs(), Integer.class);
		}

		return spec.map((row, metadata) -> row.get("seq", Integer.class))
				.all()
				.collectList()
				.map(List::size);
	}

	private static DatabaseClient.GenericExecuteSpec bindNullable(
			DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
		if (value == null) {
			return spec.bindNull(name, type);
		}
		return spec.bind(name, value);
	}
}
