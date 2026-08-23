# Privacy

SkyPulse has no analytics, advertising, crash-report uploads, telemetry, or account system.
It does not upload decoded ADS-B traffic to a SkyPulse service.

## Network access

- OpenFreeMap is contacted for map style and tiles only when map functionality is used.
- GitHub and VATSIM boundary repositories are contacted only when boundary updating is enabled or the user requests a manual update.
- RTL-TCP to the SDR driver, optional Beast TCP output, and the health endpoint bind to localhost only.

As with any network request, third-party services can receive normal technical metadata such as the source IP address.
