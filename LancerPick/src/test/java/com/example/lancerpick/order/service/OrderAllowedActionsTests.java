package com.example.lancerpick.order.service;

import com.example.lancerpick.order.dto.OrderDetailResponse.CancellationRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse.Review;
import com.example.lancerpick.order.dto.OrderDetailResponse.RevisionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class OrderAllowedActionsTests {
    @ParameterizedTest
    @CsvSource({
            "REQUESTED,true,CANCEL", "REQUESTED,false,ACCEPT REJECT",
            "AWAITING_PAYMENT,true,PAY CANCEL", "AWAITING_PAYMENT,false,CANCEL",
            "IN_PROGRESS,true,REQUEST_CANCELLATION", "IN_PROGRESS,false,CANCEL DELIVER",
            "AWAITING_CONFIRMATION,true,REQUEST_CANCELLATION CONFIRM REQUEST_REVISION",
            "AWAITING_CONFIRMATION,false,CANCEL",
            "REVISION_IN_PROGRESS,true,REQUEST_CANCELLATION", "REVISION_IN_PROGRESS,false,CANCEL DELIVER",
            "COMPLETED,true,WRITE_REVIEW", "COMPLETED,false,''",
            "REJECTED,true,''", "REJECTED,false,''", "CANCELLED,true,''", "CANCELLED,false,''"
    })
    void derivesActionsFromParticipantAndCurrentState(String state, boolean buyer, String expected) {
        assertThat(OrderAllowedActions.actions(state, buyer, null, List.of(), null))
                .containsExactlyInAnyOrder(expected.isEmpty() ? new String[0] : expected.split(" "));
    }

    @Test
    void pendingCancellationBlocksDeliveryConfirmationAndRevisionButAllowsResolutionOrDirectCancel() {
        var pending = List.of(new CancellationRequest("1", "2", "key", "reason", "PENDING", null, null, null, null));
        for (String state : List.of("IN_PROGRESS", "AWAITING_CONFIRMATION", "REVISION_IN_PROGRESS")) {
            assertThat(OrderAllowedActions.actions(state, true, null, pending, null)).containsExactly("WITHDRAW_CANCELLATION");
            assertThat(OrderAllowedActions.actions(state, false, null, pending, null))
                    .containsExactlyInAnyOrder("CANCEL", "APPROVE_CANCELLATION", "REJECT_CANCELLATION");
        }
    }

    @Test
    void revisionAndReviewCannotBeRequestedTwice() {
        var revision = new RevisionRequest("1", "2", "reason", null, null);
        assertThat(OrderAllowedActions.actions("AWAITING_CONFIRMATION", true, revision, List.of(), null))
                .containsExactlyInAnyOrder("REQUEST_CANCELLATION", "CONFIRM");
        var review = new Review("1", "2", "buyer", 5, null, null);
        assertThat(OrderAllowedActions.actions("COMPLETED", true, null, List.of(), review)).isEmpty();
    }
}
