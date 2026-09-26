module.exports = {
  preset: '@react-native/jest-preset',
  // .claude/ holds agent worktrees: whole checkouts whose tests would run twice.
  modulePathIgnorePatterns: ['<rootDir>/lib/', '<rootDir>/example/', '<rootDir>/.claude/'],
};
