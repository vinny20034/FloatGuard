
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RebalanceRequest {

    // Request identification
    private Long id;

    // Agent making the request
    private Long requestingAgentId;

    // Agent accepting the request
    private Long matchedAgentId;

    // Type of float required
    private FloatType floatType;

    // Amount requested in ZMW
    private BigDecimal amount;

    // Request status
    private RequestStatus status;

    // Request date and time
    private LocalDateTime createdAt;

    // Types of float
    public enum FloatType {
        CASH,
        E_FLOAT
    }

    // Request statuses
    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        COMPLETED,
        CANCELLED
    }

    // Default constructor
    public RebalanceRequest() {
        this.status = RequestStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // Parameterized constructor
    public RebalanceRequest(Long id,
                            Long requestingAgentId,
                            FloatType floatType,
                            BigDecimal amount) {

        this.id = id;
        this.requestingAgentId = requestingAgentId;
        this.floatType = floatType;
        setAmount(amount);

        this.status = RequestStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequestingAgentId() {
        return requestingAgentId;
    }

    public void setRequestingAgentId(Long requestingAgentId) {
        this.requestingAgentId = requestingAgentId;
    }

    public Long getMatchedAgentId() {
        return matchedAgentId;
    }

    public void setMatchedAgentId(Long matchedAgentId) {
        this.matchedAgentId = matchedAgentId;
    }

    public FloatType getFloatType() {
        return floatType;
    }

    public void setFloatType(FloatType floatType) {
        this.floatType = floatType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Rebalance amount must be greater than zero."
            );
        }

        this.amount = amount;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Accept a rebalance request
    public void acceptRequest(Long agentId) {

        if (this.status != RequestStatus.PENDING) {
            throw new IllegalStateException(
                "Only pending requests can be accepted."
            );
        }

        if (agentId == null || agentId.equals(requestingAgentId)) {
            throw new IllegalArgumentException(
                "A different agent must accept the request."
            );
        }

        this.matchedAgentId = agentId;
        this.status = RequestStatus.ACCEPTED;
    }

    // Complete a rebalance request
    public void completeRequest() {

        if (this.status != RequestStatus.ACCEPTED) {
            throw new IllegalStateException(
                "Only accepted requests can be completed."
            );
        }

        this.status = RequestStatus.COMPLETED;
    }

    // Cancel a rebalance request
    public void cancelRequest() {

        if (this.status == RequestStatus.COMPLETED ||
            this.status == RequestStatus.CANCELLED) {

            throw new IllegalStateException(
                "This request cannot be cancelled."
            );
        }

        this.status = RequestStatus.CANCELLED;
    }

    // Display request information
    @Override
    public String toString() {

        return "RebalanceRequest {" +
                "id=" + id +
                ", requestingAgentId=" + requestingAgentId +
                ", matchedAgentId=" + matchedAgentId +
                ", floatType=" + floatType +
                ", amount=" + amount +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
