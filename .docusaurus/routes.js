import React from 'react';
import ComponentCreator from '@docusaurus/ComponentCreator';

export default [
  {
    path: '/',
    component: ComponentCreator('/', '5b7'),
    routes: [
      {
        path: '/',
        component: ComponentCreator('/', '4fa'),
        routes: [
          {
            path: '/',
            component: ComponentCreator('/', '24a'),
            routes: [
              {
                path: '/archive/AddAllergy',
                component: ComponentCreator('/archive/AddAllergy', '484'),
                exact: true
              },
              {
                path: '/archive/AddMeasurements',
                component: ComponentCreator('/archive/AddMeasurements', '1ce'),
                exact: true
              },
              {
                path: '/archive/AddPrescriptions',
                component: ComponentCreator('/archive/AddPrescriptions', '0ce'),
                exact: true
              },
              {
                path: '/archive/AuditRequirements',
                component: ComponentCreator('/archive/AuditRequirements', '807'),
                exact: true
              },
              {
                path: '/archive/AutoLogin',
                component: ComponentCreator('/archive/AutoLogin', '163'),
                exact: true
              },
              {
                path: '/archive/CustomizeLoginPage',
                component: ComponentCreator('/archive/CustomizeLoginPage', '71c'),
                exact: true
              },
              {
                path: '/archive/dispensing_inventory',
                component: ComponentCreator('/archive/dispensing_inventory', '5d4'),
                exact: true
              },
              {
                path: '/archive/GetLoggedInPerson',
                component: ComponentCreator('/archive/GetLoggedInPerson', '0e2'),
                exact: true
              },
              {
                path: '/archive/HumanUserInterfaceGuideline',
                component: ComponentCreator('/archive/HumanUserInterfaceGuideline', 'a3d'),
                exact: true
              },
              {
                path: '/archive/large_data_blob_storage',
                component: ComponentCreator('/archive/large_data_blob_storage', '9a5'),
                exact: true
              },
              {
                path: '/archive/mcedt/MCEDT_HCV_Issue_Knowledge',
                component: ComponentCreator('/archive/mcedt/MCEDT_HCV_Issue_Knowledge', '830'),
                exact: true
              },
              {
                path: '/archive/Olis',
                component: ComponentCreator('/archive/Olis', '251'),
                exact: true
              },
              {
                path: '/archive/selenium_how_to',
                component: ComponentCreator('/archive/selenium_how_to', 'aa6'),
                exact: true
              },
              {
                path: '/archive/SendImmunization',
                component: ComponentCreator('/archive/SendImmunization', '17e'),
                exact: true
              },
              {
                path: '/archive/SendPatientMessage',
                component: ComponentCreator('/archive/SendPatientMessage', '186'),
                exact: true
              },
              {
                path: '/archive/ui/patientsearch',
                component: ComponentCreator('/archive/ui/patientsearch', '00d'),
                exact: true
              },
              {
                path: '/archive/ui/tickler',
                component: ComponentCreator('/archive/ui/tickler', '762'),
                exact: true
              },
              {
                path: '/archive/ui/tips',
                component: ComponentCreator('/archive/ui/tips', '09e'),
                exact: true
              },
              {
                path: '/archive/web_services/OAUTH',
                component: ComponentCreator('/archive/web_services/OAUTH', '992'),
                exact: true
              },
              {
                path: '/archive/web_services/REST',
                component: ComponentCreator('/archive/web_services/REST', '2ab'),
                exact: true
              },
              {
                path: '/dsGuideline',
                component: ComponentCreator('/dsGuideline', 'b06'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/integrator-system-architecture',
                component: ComponentCreator('/integrator-system-architecture', '3db'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/legacy-hapi-dynamic-loading',
                component: ComponentCreator('/legacy-hapi-dynamic-loading', 'ab4'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/MyDrugref',
                component: ComponentCreator('/MyDrugref', '9ed'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/Password_System',
                component: ComponentCreator('/Password_System', '43c'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/README-form-resources',
                component: ComponentCreator('/README-form-resources', '8da'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/resources-directory',
                component: ComponentCreator('/resources-directory', 'd4a'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/static/javadoc/legal/jquery',
                component: ComponentCreator('/static/javadoc/legal/jquery', '3df'),
                exact: true
              },
              {
                path: '/static/javadoc/legal/jqueryUI',
                component: ComponentCreator('/static/javadoc/legal/jqueryUI', '1bc'),
                exact: true
              },
              {
                path: '/struts-actions-detailed',
                component: ComponentCreator('/struts-actions-detailed', '981'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/struts-actions-summary',
                component: ComponentCreator('/struts-actions-summary', 'b4a'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/Testing_Exclusion_of_MCEDT_and_HinValidator_tests',
                component: ComponentCreator('/Testing_Exclusion_of_MCEDT_and_HinValidator_tests', '52b'),
                exact: true,
                sidebar: "tutorialSidebar"
              }
            ]
          }
        ]
      }
    ]
  },
  {
    path: '*',
    component: ComponentCreator('*'),
  },
];
