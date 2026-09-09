const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');

const pkg = require('../package.json');

const root = path.resolve(__dirname, '..');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * The example is an npm workspace of the library, so dependencies are hoisted
 * to the repository root and there is only ever one copy of react-native.
 * Metro finds those by walking up from this directory; it only needs to be
 * told to watch the library's sources as well.
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = getDefaultConfig(__dirname);

config.watchFolders = [root];

const resolveRequest = config.resolver.resolveRequest;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  // Resolve the library to its TypeScript sources rather than to `lib`, so
  // that editing it refreshes the app without a rebuild.
  const isLibrary =
    moduleName === pkg.name || moduleName.startsWith(`${pkg.name}/`);

  const nextContext = isLibrary
    ? {
        ...context,
        unstable_conditionNames: [
          'source',
          ...context.unstable_conditionNames,
        ],
        mainFields: ['source', ...context.mainFields],
      }
    : context;

  return resolveRequest
    ? resolveRequest(nextContext, moduleName, platform)
    : nextContext.resolveRequest(nextContext, moduleName, platform);
};

module.exports = config;
