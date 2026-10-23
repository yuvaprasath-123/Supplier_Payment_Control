package com.ofss.configuration.rule;

public class DraftRuleConflictException extends RuntimeException {
    public enum Reason {
        STALE_VERSION("The configuration version has changed; refresh before editing"),
        VERSION_NOT_DRAFT("Only draft configuration versions can be edited"),
        ROW_VERSION_EXHAUSTED("The configuration version cannot accept further changes"),
        RULE_CODE_CONFLICT("A conflicting rule already exists in this version"),
        RULE_ORDER_CONFLICT("The evaluation order is already used in this version");
        private final String message;
        Reason(String message) { this.message = message; }
        public String message() { return message; }
    }
    private final Reason reason;
    public DraftRuleConflictException(Reason reason) { super(reason.message()); this.reason = reason; }
    public Reason reason() { return reason; }
}
