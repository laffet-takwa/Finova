package com.finova.fraud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Beneficiary history for the sender to receiver pair, backing the new-recipient rule.
 * <p>
 * The id is the ordered pair, not the receiver alone: "this sender has never paid this
 * beneficiary" is the mule pattern worth scoring, whereas "somebody transferred to this account
 * recently" is not, because almost every account receives money.
 */
@Document(collection = "recipient_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientHistory {

    /** {@code senderAccountId + "->" + receiverAccountId}. */
    @Id
    private String id;

    @Indexed
    private String senderAccountId;

    @Indexed
    private String receiverAccountId;

    private Instant firstTransferredAt;
    private Instant lastTransferredAt;
    private long transferCount;

    public static String idFor(String senderAccountId, String receiverAccountId) {
        return senderAccountId + "->" + receiverAccountId;
    }

    public boolean seenSince(Instant cutoff) {
        return lastTransferredAt != null && !lastTransferredAt.isBefore(cutoff);
    }
}
