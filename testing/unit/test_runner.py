"""Unit tests for configurator"""

import unittest
import sys
import os
import time
from unittest.mock import MagicMock, mock_open, patch
from daq.runner import DAQRunner, configurator, PortInfo
from daq.host import ConnectedHost
import network

import logging
logger = logging.getLogger()
logger.level = logging.INFO
stream_handler = logging.StreamHandler(sys.stdout)
logger.addHandler(stream_handler)

class TestRunner(unittest.TestCase):
    """Test class for Configurator"""

    config = {
        'monitor_scan_sec' : '30',
        'default_timeout_sec' : '350',
        'base_conf' : 'resources/setups/baseline/module_config.json',
        'site_path' : 'local/site/',
        'initial_dhcp_lease_time' : '120s',
        'dhcp_lease_time' : '500s',
        'long_dhcp_response_sec' : '105'
    }

    def setUp(self):
        os.environ = {
            **os.environ,
            "DAQ_VERSION": "",
            "DAQ_LSB_RELEASE": "",
            "DAQ_SYS_UNAME": ""
        }
        configurator.Configurator.load_and_merge = MagicMock(return_value={})
        network.TestNetwork.__init__ = MagicMock(return_value=None)
        with patch("builtins.open", mock_open(read_data="data")):
            self.runner = DAQRunner(self.config)

    def test_reap_stale_ports(self):
        """Test port flap timeout config override"""
        self.runner.target_set_error = MagicMock()
        self.runner._port_info = {1: PortInfo()}
        self.runner._reap_stale_ports()
        self.runner.target_set_error.assert_not_called()
        ConnectedHost.__init__ = MagicMock(return_value=None)
        host = ConnectedHost()
        host.test_name = "test_test"
        port_info = PortInfo()
        port_info.flapping_start = time.time() - 1
        port_info.host = host
        self.runner._port_info = {1: port_info}

        host.get_port_flap_timeout = MagicMock(return_value=10000)
        self.runner._reap_stale_ports()
        self.runner.target_set_error.assert_not_called()

        host.get_port_flap_timeout = MagicMock(return_value=None)
        self.runner._reap_stale_ports()
        host.get_port_flap_timeout.assert_called_with(host.test_name)
        self.runner.target_set_error.assert_called()


if __name__ == '__main__':
    unittest.main()
