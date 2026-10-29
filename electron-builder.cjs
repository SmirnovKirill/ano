const { productName, version } = require('./package.json');

module.exports = {
  appId: 'ru.inno_sc.penaltycalculation',
  productName,
  files: ['desktop/*.cjs', 'desktop/app-icon.png', 'desktop/app-icon.ico'],
  win: {
    icon: 'desktop/app-icon.ico',
    target: 'zip',
    artifactName: 'penalty_calculation_service_${version}_win.${ext}',
    executableName: `Расчет неустойки версия ${version}`,
    extraResources: [
      { from: 'build/desktop/win32-x64' },
    ],
  },
};
