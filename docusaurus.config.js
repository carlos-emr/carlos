// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const {themes} = require('prism-react-renderer');
const lightTheme = themes.github;
const darkTheme = themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'OpenO EMR Documentation',
  tagline: 'Healthcare Electronic Medical Records System',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://your-site.com',
  // Set the /<baseUrl>/ pathname under which your site is served
  baseUrl: '/',

  organizationName: 'openosp', // Usually your GitHub org/user name
  projectName: 'openo-emr', // Usually your repo name

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          // Remove default docs route to use root
          routeBasePath: '/',
          // Edit URL for your repo
          editUrl: 'https://github.com/openosp/openo-emr/tree/main/',
        },
        blog: false, // Disable blog for API documentation site
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      // Replace with your project's social card
      image: 'img/openosp-social-card.jpg',
      navbar: {
        title: 'OpenO EMR',
        logo: {
          alt: 'OpenO EMR Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'tutorialSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            href: '/api',
            label: 'API Reference',
            position: 'left',
          },
          {
            href: '/javadoc/index.html',
            label: 'Javadoc',
            position: 'left',
            target: '_blank',
          },
          {
            href: 'https://github.com/openosp/openo-emr',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Getting Started',
                to: '/',
              },
              {
                label: 'API Documentation',
                to: '/api',
              },
              {
                label: 'Javadoc Reference',
                href: '/javadoc/index.html',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/openosp/openo-emr',
              },
              {
                label: 'OpenOSP',
                href: 'https://openosp.ca',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Password System',
                to: '/Password_System',
              },
              {
                label: 'Struts Actions',
                to: '/struts-actions-detailed',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} OpenOSP. Built with Docusaurus.`,
      },
      prism: {
        theme: lightTheme,
        darkTheme: darkTheme,
        additionalLanguages: ['java', 'xml', 'sql', 'bash'],
      },
    }),
};

module.exports = config;