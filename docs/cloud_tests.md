# Cloud Connection Testing

A number of additional setup steps are required for enabling testing against "smart devices"
that communicate with the cloud. The tests themselves are part of the `subset/cloud/test_udmi`
module included in the standard DAQ distro. The same basic device-to-cloud validation test
pipeline can be done manually and automatically (through DAQ); it's instructive to fully
understand the manual test pipeline before engaging with the automated setup.

## Manual Test Pipeline

The overall device-to-cloud pipeline looks something like the following:

* Device sends data to the cloud. There's two kinds of devices:
  * A faux _reference design_ device called [pubber](pubber.md), which is a completely contained
  software device.
  * An actual physical device. The setup and configuration of that device will be manufacturer
  dependent and so is out of scope for this (DAQ) documentation.
* A configured GCP IoT Core project, registry, and device entry. The
[GCP docs for IoT Core](https://cloud.google.com/iot/docs/how-tos/devices) describe the basics. The
key part is the _authentication key_ (hahaha) that needs to be setup between the local device and
cloud device entry.
* The IoT Core registry is configured with a _PubSub topic_ (not to be confused with an _MQTT topic_),
that provides the bridge between incoming data and consumers of that data. See the GCP documentation
on PubSub for more details.
* (optional) The `gcloud` command line can be used to validate that data is being sent from the
device to the cloud. Something like
`gcloud pubsub subscriptions pull --auto-ack projects/{project}/subscriptions/{sub_id}`.
(Complete documentation for how to use `gcloud` commands is out of scope of this documentation.)
* The [validator tool](validator.md) is what programmatically validates a device data stream, and
is what is ultimately used by `test_udmi` to validate device-cloud communication.

## Base Local Test Setup

* The `udmi` module needs to be enabled in build. When running `cmd/build` there should be a line
like `subset/cloud/Dockerfile.test_udmi` in the startup logs.
This is enabled through the `host_tests` config parameter,
which can be set to `config/modules/all.conf` if necessary. On startup, there should be a log
message that includes `udmi`:
```
Jun 22 08:32:52 runner   INFO    Configured with tests pass, fail, ping, bacnet, mudgee, nmap, discover, switch, macoui, bacext, tls, password, udmi, manual
```
* A testing gcp service account `gcp_cred` needs to be setup as described in
[service account setup instructions](service.md).
* The system's default `module_config` needs to enable the `udmi` test, e.g. as per
`resources/setups/baseline/module_config.json`. This can be validated by (runtime) checking
`inst/run-port-01/nodes/udmi01/tmp/module_config.json` to see if it has something like the following:
```
    "udmi": {
      "enabled": true
    }
```
* `site_path` config needs to point to a site definition directory, or defaults to `local/site`.
This contains all the site-specific information about devices needed for testing.
* `{site_path}/mac_addrs/{mac_addr}/module_config.json` needs to have a `device_id` defined, e.g.
as in `resources/test_site/mac_addrs/3c5ab41e8f0b/module_config.json`.
* The GCP IoT Core setup needs to have a proper registry and device configred. This can either
be done manually or using the [registrar tool](registrar.md) tool.

## Integration Testing

If developing cloud-tests, then the CI build system also needs to have a service account configured
pointing at a suitable GCP project. To run cloud-based tests, setup the Travis `GCP_BASE64_CRED`
env variable with a `base64` encoded service account key for your project. It's recommended to
use a dedicated key with a nice name like `daq-travis`, but not required. Encode the key value
as per below, and cut/paste the resulting string into a
[Travis environment variable](https://docs.travis-ci.com/user/environment-variables/#defining-variables-in-repository-settings)
for a `GCP_BASE64_CRED` variable. Note the `-w 0` option is required for proper parsing/formatting,
as there can't be any newlines in the copied string.

<code>
$ <b>base64 -w 0 local/gcp_service_account.json</b>
ewoICJ1eXBlIjogInNlcnZpY2VfYWNjb3VudCIsCiAgInByb2plY3RfaWQiOiAiYm9zLWRhcS10ZXN0aW5nIiwKICAicHJpd
&hellip;
iOiAiaHR0cHM6Ly93LWRhcS10ZXN0aW5nLmlhbS5nc2VydmljZWFjY291bnQuY29tIgp9Cg==
</code>

### Travis CI Testing

* Run the [registrar tool](registrar.md) to properly configure the cloud project.
* `gcp_topic` config to `local/system.conf` as described in this doc.
* Configure test subsystem with proper cloud endpoint in `{test_site}/cloud_iot_config.json`.
* Configure the DUT with the proper cloud device credentials (device specific). For _faux_ devices, this means copying
the associated `rsa_private.pkcs8` file to something like `inst/faux/daq-faux-2/local/` (exact path depends on which faux).
* Test with `bin/registrar`, `pubber/bin/run`, and `bin/validate` manually, before integrated testing through DAQ.

### Is my Travis set up correctly?

If Travis is set up correctly, you should see messages at the beginning of the log file:
```
Setting environment variables from repository settings
$ export DOCKER_USERNAME=[secure]
$ export DOCKER_PASSWORD=[secure]
$ export GCP_BASE64_CRED=[secure]
```

Further down there would be more details about the cred itself:
```
Running test script testing/test_aux.sh
Writing test results to inst/test_aux.out and inst/test_aux.gcp
Decoding GCP_BASE64_CRED to inst/config/gcp_service_account.json
base64 wc: 1 1 3097
GCP service account is "daq-travis@daq-testing.iam.gserviceaccount.com"
```

If the `3097` character count is wildly off, then likely something went wrong with the newlines.

### Travis Build For "External" Pull Requests

Travis will not use encrypted environment variables when testing against pull requests
from foreign github repositories, even if you've forked from another repository that you
have full control of via Github. Travis authorization != Github authorization, even if
you sign into Travis using Github! This is as it should be b/c security. see the following
for more info:

- https://docs.travis-ci.com/user/environment-variables/#defining-variables-in-repository-settings
- https://docs.travis-ci.com/user/pull-requests/#pull-requests-and-security-restrictions

If your test is failing from a PR, you'll see something like in a similar log location:

```
Encrypted environment variables have been removed for security reasons.
See https://docs.travis-ci.com/user/pull-requests/#pull-requests-and-security-restrictions
Setting environment variables from .travis.yml
$ export DOCKER_STARTUP_TIMEOUT_MS=60000
$ export DAQ_TEST=aux
```

### Other Travis Caveats

Take note the URL in your browser's address bar when running Travis. You might be on either
<code>travis-ci<b>.com</b></code> or <code>travis-ci<b>.org</b></code>. Any particular setup
may end up across both sites for undetermined reasons. Please consult with your browser's
exact URL for more clarity.
