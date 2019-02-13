package controllers;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.util.concurrent.CompletionStage;

/**
 * @author fo
 */
class Authorized extends Action.Simple {

  private static final String OIDC_CLAIM_SUB = "oidc_claim_sub";
  private static final String OIDC_CLAIM_EMAIL = "oidc_claim_email";
  private static final String OIDC_CLAIM_PROFILE_ID = "oidc_claim_profile_id";

  @Override
  public CompletionStage<Result> call(Http.Context ctx) {
    if (ctx.request().hasHeader(OIDC_CLAIM_SUB)
      && ctx.request().hasHeader(OIDC_CLAIM_EMAIL)
      && ctx.request().hasHeader(OIDC_CLAIM_PROFILE_ID)
    ) {
      ctx.request().setUsername(ctx.request().getHeader(OIDC_CLAIM_EMAIL));
      ctx.args.put("profile", ctx.request().getHeader(OIDC_CLAIM_PROFILE_ID));
    }
    return delegate.call(ctx);
  }

}
