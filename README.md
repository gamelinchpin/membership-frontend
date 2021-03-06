```
                            __                            __
 /'\_/`\                   /\ \                          /\ \      __
/\      \     __    ___ ___\ \ \____     __   _ __   ____\ \ \___ /\_\  _____
\ \ \__\ \  /'__`\/' __` __`\ \ '__`\  /'__`\/\`'__\/',__\\ \  _ `\/\ \/\ '__`\
 \ \ \_/\ \/\  __//\ \/\ \/\ \ \ \L\ \/\  __/\ \ \//\__, `\\ \ \ \ \ \ \ \ \L\ \
  \ \_\\ \_\ \____\ \_\ \_\ \_\ \_,__/\ \____\\ \_\\/\____/ \ \_\ \_\ \_\ \ ,__/
   \/_/ \/_/\/____/\/_/\/_/\/_/\/___/  \/____/ \/_/ \/___/   \/_/\/_/\/_/\ \ \/
                                                                          \ \_\
                                                                           \/_/
```

# Membership Frontend

## Ubuntu

In an ideal world, your Ubuntu package install would be:

```
$ sudo apt-get install nginx openjdk-7-jdk ruby ruby-dev nodejs npm
```

### [Node](http://nodejs.org/) & [NPM](https://github.com/npm/npm/releases)

See Joyent's instructions on [installing Node & NPM on Ubuntu](https://github.com/joyent/node/wiki/Installing-Node.js-via-package-manager#ubuntu-mint-elementary-os).
If the available [nodejs](http://packages.ubuntu.com/trusty/nodejs) or [NPM](http://packages.ubuntu.com/trusty/npm)
package for your version of Ubuntu is old, you'll [probably](http://askubuntu.com/questions/49390/how-do-i-install-the-latest-version-of-node-js)
want to install [chris-lea's PPA](https://launchpad.net/~chris-lea/+archive/node.js),
which includes both Node.js and NPM.

## General Setup


1. Go to project root
1. If you don't have `bower`, `grunt`, or `sass`, run `./setup-tools.sh` to install them globally on your system.
1. Run `./setup.sh` to install project-specific client-side dependencies.
1. Add the following to your `/etc/hosts`

   ```
   127.0.0.1   mem.thegulocal.com
   ```

1. ./nginx/setup.sh
1. Download our private keys from the `membership-private` S3 bucket. You will need an AWS account so ask another dev.

    If you have the AWS CLI set up you can run
    ```
    aws s3 cp s3://membership-private/DEV/membership-keys.conf /etc/gu
    ```
1. In ~/.bash_profile add
    ```
    export AWS_ACCESS_KEY=<access-key-id>

    export AWS_SECRET_KEY=<secret-key>
    ```

## Run
The app normally runs on ports 9100 respectively.
You can run the following commands to start them (separate console windows)

```
 nginx/setup.sh
./start-frontend.sh
```

## Run membership locally with Identity on NGW
To be able to go through the registration flow locally you will need to have the Identity API and Identity project in NGW running.

Identity repo: https://github.com/guardian/identity

Run through the set up instructions. Once complete you will just need to run
```
 nginx/setup.sh
 ./start-api.sh
```

NGW Frontend repo: https://github.com/guardian/frontend

Run through the set up instructions. Once complete you will just need to run
```
nginx/setup.sh
./sbt
project identity
idrun
```

## To run frontend tests

+ $ karma start

# Grunt Tasks

## Watch and compile front-end files
+ $ grunt watch

## Compile front-end files
+ $ grunt compile


# Images

## SVGs

Running the grunt task `shell:svgencode` will automatically base64 encode files in `assets/svgs` and create a class and mixin for each one in a generated SCSS file. The classes/mixins are named `icon-<filename without extension>`.

### Adding new SVGs

When adding new SVGs to the `assets/svgs` directory run the file through [SVGO](https://github.com/svg/svgo) first to optimise it. For Mac users there is an app for doing this: [SVGO GUI](https://github.com/svg/svgo-gui)

## Asset hashing

By default all of the images within ```assets/images/``` will have an asset hash applied to them when the Grunt compile task is ran in production mode. If you wish to have assets without an asset hash please place them within ```assets/images/noAssetHash```. These images will have a cache lifetime set by the server and you will have to manually bust the cache by changing the filename.


# Updating AMIs
We use [packer](http://www.packer.io) to create new AMIs, you can download it here: http://www.packer.io/downloads.html. To create an AMI, you must set `AWS_ACCESS_KEY` and `AWS_SECRET_KEY` as described above.

## Building

To add your requirements to the new AMI, you should update `provisioning.json`. This will probably involve editing the `provisioners` section, but more information can be found in the [packer docs](http://www.packer.io/docs). Once you are ready, run the following:
```
packer build provisioning.json
```
This will take several minutes to build the new AMI. Once complete, you should see something like:
```
eu-west-1: ami-xxxxxxxx
```


## Deploying

1. Turn off continuous deployment in RiffRaff
1. Update the CloudFormation parameter `ImageId` <b>(make a note of the current value first)</b>
1. Increase the autoscale group size by 1
1. Test the new box
1. If it doesn't work, revert the value of `ImageId`
1. Run a full deploy in RiffRaff
1. Decrease autoscale group size by 1
1. Re-enable continous deployment


# Test Users

See https://sites.google.com/a/guardian.co.uk/guardan-identity/identity/test-users for details of how we do test users.
Note that we read the shared secret for these from the `identity.test.users.secret` property in `membership-keys.conf`.


# Committing config credentials

For the Membership project, we put both `DEV` and `PROD` credentials in `membership-keys.conf` files in the private S3 bucket `membership-private`, and if private credentials need adding or updating, they need to be updated there in S3.

You can download and update credentials like this 

    aws s3 cp s3://membership-private/DEV/membership-keys.conf /etc/gu
    aws s3 cp /etc/gu/membership-keys.conf s3://membership-private/DEV/
    
For a reminder on why we do this, here's @tackley on the subject:

>NEVER commit access keys, passwords or other sensitive credentials to any source code repository*. 

>That's especially true for public repos, but also applies to any private repos. (Why? 1. it's easy to make it public and 2. every person who ever worked on your project almost certainly has a copy of your repo somewhere. It's too easy for a subsequently-disaffected individual to take advantage of this.)

>For AWS access keys, always prefer to use instance profiles instead.

>For other credentials, either use websys's puppet based config distribution (for websys managed machines) or put them in a configuration store such as DynamoDB or a private S3 bucket.
