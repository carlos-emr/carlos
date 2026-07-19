/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */

// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  // By default, Docusaurus generates a sidebar from the docs folder structure
  tutorialSidebar: [
    'intro',
    {
      type: 'category',
      label: 'Getting Started',
      items: [
        'getting-started/installation',
        'getting-started/configuration',
        'getting-started/development-setup',
      ],
    },
    {
      type: 'category',
      label: 'Development',
      items: [
        'development/architecture',
        'development/struts2-migration',
        'development/2action-pattern',
        'development/spring-configuration',
        'development/database-schema',
        'development/security',
      ],
    },
    {
      type: 'category',
      label: 'Healthcare Features',
      items: [
        'healthcare/patient-management',
        'healthcare/clinical-workflows',
        'healthcare/billing-integration',
        'healthcare/reporting',
        'healthcare/integrations',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api/rest-endpoints',
        'api/authentication',
        'api/data-models',
      ],
    },
    {
      type: 'category',
      label: 'Deployment',
      items: [
        'deployment/docker',
        'deployment/production',
        'deployment/monitoring',
      ],
    },
  ],

  // But you can create a sidebar manually
  /*
  tutorialSidebar: [
    'intro',
    'hello',
    {
      type: 'category',
      label: 'Tutorial',
      items: ['tutorial-basics/create-a-document'],
    },
  ],
   */
};

module.exports = sidebars;