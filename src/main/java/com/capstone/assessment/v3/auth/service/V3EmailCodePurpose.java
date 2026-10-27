package com.capstone.assessment.v3.auth.service;

/** Why a 6-digit code is emailed; each purpose has its own subject and wording. */
public enum V3EmailCodePurpose {
    REGISTRATION(
            "Verify your Marka teacher account",
            "Teacher email verification",
            "Verify your email address",
            "Enter this code in Marka to continue registration.",
            "If you did not request this registration, you may ignore this email."),
    VERIFY_EMAIL(
            "Verify your Marka email address",
            "Email verification",
            "Verify your email address",
            "Enter this code in Marka Settings to verify this email address.",
            "If you did not ask for this, you may ignore this email."),
    EMAIL_CHANGE(
            "Confirm your new Marka email address",
            "Email change",
            "Confirm your new email address",
            "Enter this code in Marka to use this address for your account.",
            "If you did not ask to change your email, ignore this email. Your account stays the same."),
    PASSWORD_RESET(
            "Your Marka password reset code",
            "Password reset",
            "Reset your password",
            "Enter this code in Marka to set a new password.",
            "If you did not ask to reset your password, ignore this email. Your password stays the same.");

    private final String subject;
    private final String label;
    private final String heading;
    private final String instruction;
    private final String ignoreNote;

    V3EmailCodePurpose(String subject, String label, String heading, String instruction, String ignoreNote) {
        this.subject = subject;
        this.label = label;
        this.heading = heading;
        this.instruction = instruction;
        this.ignoreNote = ignoreNote;
    }

    public String subject() {
        return subject;
    }

    public String label() {
        return label;
    }

    public String heading() {
        return heading;
    }

    public String instruction() {
        return instruction;
    }

    public String ignoreNote() {
        return ignoreNote;
    }
}
