const { name, productName, version } = require('./package.json');

module.exports = {
  appId: 'ru.inno_sc.penaltycalculation',
  productName,
  files: ['desktop/*.cjs'],
  win: {
    target: 'zip',
    executableName: `${name}-${version}`,
    extraResources: [
      { from: 'build/desktop/win32-x64' },
    ],
  },
};
