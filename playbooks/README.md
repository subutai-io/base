Acceptance tests for Subutai Social
===================================

In our tests we are using two testing libraries: **Serenity** + **Sikuli**

**Serenity** is an open source library that helps us write higher quality automated acceptance tests faster.

[http://thucydides.info](http://thucydides.info) based on JAVA.

**Sikuli** automates anything we are see on the screen. It uses image recognition to identify and control GUI components.

Open source too. 

[http://www.sikuli.org/](http://www.sikuli.org/) supporting JAVA.

For clicks by elements and testing plugins we using **Sikuli**, all another places we are verifying with **Serenity**.

Main blocks into Serenity: **Pages**, **Steps**, **DefSteps**, **Stories**

**Pages:** This is Java classes extended from PageObject class. Here we are writing all web elements for next work.

Base Pages          |                      |
--------------------|-----------------------
AboutPage           |  LoginPage
AccountSettings     |  MonitoringPage
AdvancedPage        |  NetworkSettings
BazaarPage          |  PeerRegistrationPage
BlueprintsPage      |  PeerSettingsPage
CommonPages         |  PgpPlugin
ConsolePage         |  PluginIntegratorPage
ContainersPage      |  PluginsPage
EnvironmentsPage    |  ResourceHostsPage
HomePage            |  ResourceNodesPage
KurjunPage          |  RoleManagementPage
KurjunSettingsPage  | TokensPage
TrackerPage         | UserManagementPage

**Steps:** Using Web Elements from Pages we creating base steps: clicks, enters, waits, types, etc.

**DefSteps:** Here we are using our Steps and concatenating them with JBehave high language level specification. 
 (looks like as cucumber)

**Stories:** Using DefSteps we are creating testing story and scenarios for any verifications.

You are can run test on the local machines.
For this you need clone directory playbooks and run script 
`./run_test_qa.sh` with parameters: `-h; -r; -s; -S; -m; -M; -l; -L `

Parameter       | Description 
----------------|----------------------
-m              | Set Management Host First:  IP
-M              | Set Management Host Second: IP
-l              | Observe List of All Playbooks
-s              | Choice of Playbooks for run
                | “playbook1.story, playbook2.story” ...  Start a few Playbooks
-r              | Start acceptance tests
-h              | Get Help info

Dependencies: **Maven3**, **Java7/8** also need additional packages **OpenCV**

For Ubuntu need install next packages:
```scss
sudo add-apt-repository ppa:gijzelaar/opencv2.4
sudo apt-get update
sudo apt-get libcv-dev
sudo apt-get install libtesseract3 
```
What is Serenity
-------------------------------------------

Define your requirements and acceptance criteria
When you use Serenity, you start with the requirements you need to implement.
These are often expressed as user stories with acceptance criteria that help clarify the requirements.
It is these Acceptance Criteria that we automate with Serenity.

Automate your acceptance criteria
Next, you describe your acceptance criteria in high-level business terms.
Developers record these acceptance criteria using either a BDD tool such as Cucumber or JBehave,
or simply in Java using JUnit, so that Serenity can run them.

Implement the tests
Developers now implement the acceptance criteria, so that they can run against the actual application.
Under the hood, tests are broken down into nested steps for better readability and easier maintenance.
If you are testing a web application,
Serenity provides great built-in support for the popular open source Selenium 2/WebDriver library.

Report on test results
Serenity provides detailed reports on the test results and execution, including:
A narrative for each test
Screen shots for each step in the test
Test results including execution times and error messages if a test failed

What is Sikuli
-------------------------------------------

SikuliX automates anything you see on the screen of your desktop computer running Windows, Mac or some Linux/Unix.
It uses image recognition powered by OpenCV to identify and control GUI components. 

This is handy in cases when there is no easy access to a GUI's internals or the source code of the application 
or web page you want to act on.

SikuliX supports as scripting languages
Python language level 2.7 (supported by Jython)
Ruby language level 1.9 and 2.0 (supported by JRuby)
… and you can use it in Java programming and programming/scripting with any Java aware programming/scripting 
language (Jython, JRuby, Scala, Clojure, …).

Though SikuliX is currently not available on any mobile device,
it can be used with the respective emulators on a desktop computer or based on VNC solutions.

Besides locating images on a screen SikuliX can run the mouse and the keyboard to interact 
with the identified GUI elements. This is available for multi monitor environments and even 
for remote systems with some restrictions.

SikuliX comes with basic text recognition (OCR) and can be used to search text in images. 
This feature is powered by Tesseract.

SikuliX is a Java application, that works on Windows XP+, Mac 10.6+ and most Linux/Unix systems.
For Windows, Mac and Ubuntu 12.04+ it is complete and should normally work out of the box.
For other Linux/Unix systems there usually are a few prerequisites to be setup.