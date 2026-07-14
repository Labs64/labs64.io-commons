// GENERATED from x-labs64-auth by OpenApiAuthPreprocessor — do not edit.
// Tier 1 edge policies for module "test-module".

@id("test-module::ping")
permit(
  principal,
  action == Labs64IO::Action::"invoke",
  resource == Labs64IO::ApiOperation::"test-module::ping"
);
