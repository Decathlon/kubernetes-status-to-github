# Step by step: installing "kube-to-github" controller

There are 5 files to set up. Most can be taken directly.

These are kubernetes resources, you can deploy them as any other resources, using FluxCD for instance or by simply `kubectl apply` them.

## Decide a namespace where these resources will be deployed

It is the bases... On the example file, we will assume `github` namespace to be created.

## Documentation and base files

### Create an OAuth 2 client_credential entry on your oidc provider

This client will be use when transferring event from this capture part to the process part. 

At this step, you will have a `client_id` and a `client_secret`.

There is many way to inject those data in a kubernetes pod. Here we will simply create a basic secret. Not the most secure way, but it will do the job here.
You can create a secret like [this one](secret.yaml) and apply it to your cluster (do not forget to base64 encode if you copy/paste the secret in the file)

## Core kubernetes resources

### Create a role for the controller.

The basic role is [like this one](role.yaml). You will have to be able to read the kubernetes object you want to follow.

### The custom resource descriptor (crd)

You can apply directly the [crd](crd.yaml).

## Properties for the kubernetes controller: application.yaml in configmap

The kubernetes controller is configured either with environment variable or with a file from for instance a configmap. 

For this capture, we will use the [capture configuration file](configmap.yaml).

You must edit the file and set the transfer url and your OIDC provider correctly.   


## Last step: create the controller

The deployment object for the controller is the [capture.yaml file](capture.yaml)

In this file, there is nothing special to do except validate the image version if you want to use the latest stable one.

## Check that is it correctly installed

The controller is a standard pod, named "kube-to-status".

You can check if it is up with the `kubectl get pod -n github` command.

You may also check if the crd is set. The command: `kubctl get ghd` has to display the message: "No resources found in <your namespace name> namespace.".

If so, the controller in capture mode is set up!
