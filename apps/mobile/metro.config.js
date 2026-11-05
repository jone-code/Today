const path = require("node:path");
const { getDefaultConfig } = require("expo/metro-config");

const projectRoot = __dirname;
const config = getDefaultConfig(projectRoot);

// The repository also contains the web app, which uses a newer React patch.
// Keep Metro on the React version paired with Expo SDK 53 / React Native 0.79.
config.resolver.extraNodeModules = {
  ...(config.resolver.extraNodeModules || {}),
  react: path.resolve(projectRoot, "node_modules/react"),
};

const defaultResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === "react" || moduleName.startsWith("react/")) {
    return {
      type: "sourceFile",
      filePath: require.resolve(moduleName, { paths: [path.join(projectRoot, "node_modules")] }),
    };
  }
  return defaultResolveRequest
    ? defaultResolveRequest(context, moduleName, platform)
    : context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
