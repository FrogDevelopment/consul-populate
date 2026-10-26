package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class BitbucketWebhookPayloadHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BitbucketWebhookPayloadHandler handler = new BitbucketWebhookPayloadHandler();

    @Test
    void getEventHeaderKey_shouldReturnEventKey() {
        assertThat(handler.getEventHeaderKey()).isEqualTo("X-Event-Key");
    }

    @Test
    void getExpectedEventValue_shouldReturnRepoPush() {
        assertThat(handler.getExpectedEventValue()).isEqualTo("repo:push");
    }

    @Test
    void getSignatureHeaderKey_shouldReturnHubSignature() {
        assertThat(handler.getSignatureHeaderKey()).isEqualTo("X-Hub-Signature");
    }

    @Test
    void extractBranchName_shouldExtractBranchFromValidPayload() {
        // given
        var payload = createBitbucketPayload("main");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isPresent().hasValue("main");
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenPushIsMissing() {
        // given
        var payload = objectMapper.createObjectNode();
        payload.put("other_field", "value");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenPushIsNull() {
        // given
        var payload = objectMapper.createObjectNode();
        payload.putNull("push");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenChangesIsMissing() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenChangesIsEmpty() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();
        push.set("changes", changes);
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenChangesIsNotArray() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        push.put("changes", "not-an-array");
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenNewNodeIsNull() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();
        var change = objectMapper.createObjectNode();
        change.putNull("new");
        changes.add(change);
        push.set("changes", changes);
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenNameNodeIsMissing() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();
        var change = objectMapper.createObjectNode();
        var newBranch = objectMapper.createObjectNode();
        newBranch.put("type", "branch"); // has other fields but no "name"
        change.set("new", newBranch);
        changes.add(change);
        push.set("changes", changes);
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenNameNodeIsNull() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();
        var change = objectMapper.createObjectNode();
        var newBranch = objectMapper.createObjectNode();
        newBranch.putNull("name");
        change.set("new", newBranch);
        changes.add(change);
        push.set("changes", changes);
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenNoNewEntryExists() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();
        var change = objectMapper.createObjectNode();
        var old = objectMapper.createObjectNode();
        old.put("name", "deleted-branch");
        change.set("old", old);
        changes.add(change);
        push.set("changes", changes);
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldHandleFeatureBranch() {
        // given
        var payload = createBitbucketPayload("feature/my-feature");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isPresent().hasValue("feature/my-feature");
    }

    @Test
    void extractBranchName_shouldReturnFirstNewBranchWhenMultipleChanges() {
        // given
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();

        // First change without "new"
        var change1 = objectMapper.createObjectNode();
        var old = objectMapper.createObjectNode();
        old.put("name", "deleted-branch");
        change1.set("old", old);
        changes.add(change1);

        // Second change with "new"
        var change2 = objectMapper.createObjectNode();
        var newBranch = objectMapper.createObjectNode();
        newBranch.put("name", "develop");
        change2.set("new", newBranch);
        changes.add(change2);

        push.set("changes", changes);
        payload.set("push", push);

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isPresent().hasValue("develop");
    }

    @Test
    void extractBranchNames_shouldReturnEveryUpdatedBranchOfASinglePush() {
        // given - a push updating feature and main at once
        var payload = createBitbucketPayload("feature", "main");

        // when
        var result = handler.extractBranchNames(payload);

        // then
        assertThat(result).containsExactly("feature", "main");
    }

    @Test
    void extractBranchNames_shouldIgnoreChangesWithoutNewBranch() {
        // given - the first change deletes a branch (no "new"), the second one updates develop
        var payload = createBitbucketPayload("develop");
        var deletion = objectMapper.createObjectNode();
        var old = objectMapper.createObjectNode();
        old.put("name", "deleted-branch");
        deletion.set("old", old);
        ((ArrayNode) payload.get("push").get("changes")).insert(0, deletion);

        // when
        var result = handler.extractBranchNames(payload);

        // then
        assertThat(result).containsExactly("develop");
    }

    @Test
    void extractBranchNames_shouldReturnEmptyWhenPushIsMissing() {
        // when
        var result = handler.extractBranchNames(objectMapper.createObjectNode());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldStillReturnTheFirstBranchOfAMultiBranchPush() {
        // given
        var payload = createBitbucketPayload("feature", "main");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).hasValue("feature");
    }

    private ObjectNode createBitbucketPayload(String... branchNames) {
        var payload = objectMapper.createObjectNode();
        var push = objectMapper.createObjectNode();
        var changes = objectMapper.createArrayNode();
        for (var branchName : branchNames) {
            var change = objectMapper.createObjectNode();
            var newBranch = objectMapper.createObjectNode();
            newBranch.put("name", branchName);
            change.set("new", newBranch);
            changes.add(change);
        }
        push.set("changes", changes);
        payload.set("push", push);
        return payload;
    }
}
