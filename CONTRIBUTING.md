# Contributing

If you are on this page it means you are almost ready to contribute proposing changes, fixing issues or anything else.
So, **thanks a lot for this**!! :tada::+1:

## What are you talking about? Pull Request? Merge? Push?

If you are not familiar with Git and GitHub terms you can check a complete [glossary](https://help.github.com/articles/github-glossary/) on the GitHub website.


## How can I contribute?

### Discussing features

The [Discussion board](https://github.com/Decathlon/kubernetes-status-to-github/discussions) is open for everyone to ask question, discuss new features, and so on. If you want to interact with the developers this is the main entry point.  

### Reporting an issue

If you find anything which is not working well or as expected you can [open an issue](https://github.com/Decathlon/kubernetes-status-to-github/issues/new/choose).

Before to open the issue please check if there is one similar already opened. It will save us hours and will allow us to answer you more quickly with the desired hotfix or implementation.

> **NOTE:** if looking for existing issues you will find the same problem, or similar, in **closed** state, please refer to this issue (with its number) when you are opening your one. It is maybe a regression we didn't see. In this way you will help to go faster and to find a definitive solution to the recurrent problem.
When you are opening an issue, please be sure to report as much information as you can to allow us to replicate the problem and faster find the solution.

### Code contribution

If you are a developer, if you want to directly fix a problem you are the best one! :clap::clap:

If you want to create a new feature, please discuss it on the [Discussion board](https://github.com/Decathlon/kubernetes-status-to-github/discussions) before acting. 

The code contribution workflow is:

1. Fork this repository (as you don't have a direct write access to the main one.
2. Create your code, `Commit` and `Push the code` on your forked repo
3. Create a GitHub `Pull Request` to our **main** branch (which is the main one for the coming version).

We will take the time to review your code, make some comments or asking information if needed. But, as you took time to help us, we will take in serious consideration what you are proposing.
To quickly have your code available on production, please take care and read our [Contribution acceptance criteria](#contribution-acceptance-criteria)

### Pull Request guidelines

When you open your Pull Request provide as much information as possible.

- For an issue, describe what you are fixing with your pull request and how you had found the defect.
- If you are proposing an enhancement, describe what you are adding to the code (new function, performance enhancement, documentation update, changing an existing function, ...).

#### Version

When a Pull Request is merged on `main` branch, a new docker image will be built. it will be possible to find this image tag via a filter on the short commit sha1.  

When a release is done, a new semver tagged image will be produced.

### Contribution acceptance criteria

We love maintainable software! We are happy when someone else than us is able to take the code, **understand it** and is able to change it.
To reach this goal we fixed some rule:

1. Be sure your code compile: no syntax error, no missing library, ...
2. Add comments on the code if you want to explain better what is happening in the code.
3. Add documentation for any API, if needed, or functional explaining what changed/added with your code.
4. After you proposed the PullRequest. If there is red check on it, it means there is something which is not validated either linked to security, to code style, to... You have to fix that.

If you respect all these rules it is easier to accept a Pull-Request.
