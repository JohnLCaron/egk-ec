# EGK Election Record JSON directory and file layout, version 2.0ec

draft 04/13/2024

## Public Election Record files

````
topdir/
    constants.json
    election_config.json
    election_initialized.json
    encrypted_tally.json
    manifest.json
    tally.json
    
    encrypted_ballots/
      eballot-<ballotId>.json
      eballot-<ballotId>.json
      eballot-<ballotId>.json
      ...
      
    challenged_ballots/
      dballot-<ballotId>.json
      dballot-<ballotId>.json
      dballot-<ballotId>.json
      ...
````   

The encrypted_ballots directory may optionally be divided into "device" subdirectories.

If using ballot chaining, each such subdirectory is a separate ballot chain, like this:

````
topdir/
    ...
    encrypted_ballots/
       deviceName1/
          ballot_chain.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
          ...
        deviceName2/
          ballot_chain.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
        deviceName3/
           ...
```` 

Files/directories may be absent, depending on the workflow stage:


| Name                      | Type                    | Workflow stage      |
|---------------------------|-------------------------|---------------------|
| constants.json            | ElectionConstantsJson   | start               |
| manifest.json             | ManifestJson            | start               |
| election_config.json      | ElectionConfigJson      | key ceremony input  |
| election_initialized.json | ElectionInitializedJson | key ceremony output |
| eballot-\<ballotId>.json  | EncryptedBallotJson     | encryption output   |
| encrypted_tally.json      | EncryptedTallyJson      | tally output        |
| tally.json                | DecryptedTallyJson      | decryption output   |
| dballot-\<ballotId>.json  | DecryptedBallotJson     | decryption output   |

* The encrypted_ballots director(ies) contain all ballots, cast or challenged.
* The challenged_ballots directory contain only challenged ballots that have been decrypted.
* DecryptedTallyJson and DecryptedBallotJson use the same schema (DecryptedTallyOrBallotJson)

The entire election record may be zipped; the library can read from the zip file, eg for verification.

## Private files

These files are not part of the election record, but are generated for internal use.
In production, these must be stored in a secure place.

### KeyCeremony

Each trustee maintains their own private copy of their crypto data. They must keep this data secret, to ensure the
security of the election.

````
private_data/trustees/
    decryptingTrustee-{trusteeName}.json
````    

### Encryption

During the encryption stage, input plaintext ballots are checked for consistency against the manifest. 
The ones that fail are not encrypted, and are placed in a private directory for examination by election officials.

````
private_data/input/
       pballot-<ballotId>.json
       pballot-<ballotId>.json
       ...
````    
