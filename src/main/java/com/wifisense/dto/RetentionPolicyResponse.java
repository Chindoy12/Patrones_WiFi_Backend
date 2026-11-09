package com.wifisense.dto;

/**
 * One retention rule as the administrators see it: what is kept, for how long, where it is configured (an
 * environment variable of the backend in Railway) and what happens afterwards. Read-only: the values are changed
 * in the deployment, never from the page.
 */
public record RetentionPolicyResponse(String code, String title, Long days, String what, String setting,
                                      String afterwards) {
}
