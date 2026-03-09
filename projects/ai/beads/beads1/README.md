# Example

Example Projects

## Getting Started

https://claude.ai/share/26fc5a5e-a0fd-4c88-9e6f-5627bfd01ae9
brew install dolt

https://share.google/aimode/RfrM6IS1TwKIt9FjC


https://steve-yegge.medium.com/the-beads-revolution-how-i-built-the-todo-system-that-ai-agents-actually-want-to-use-228a5f9be2a9

## Prompts

1. Can you come up with a plan to build the simplest web application that shows a message(for example:hello world)
   2. Do not write any code yet. Only create the beads tickets and sub-tickets in an epic.
   3. Be ready to implement the epic when I ask you to
   3. The backend should use python and flask
   3. Front end should use typescript and react
7. Please restart from scratch. Remove all artifacts you created in this session
   — including the beads tickets, the CLAUDE.md file, and any code files or     
  directories. 
8. Can you come up with a plan to build the simplest web application that shows a message(for example:hello world)
   2. Do not write any code yet. Only create the beads tickets and sub-tickets in an epic.
   3. Be ready to implement the epic when I ask you to
   3. The backend should use python and flask
   3. Front end should use typescript and react
   4. write sub-tickets for very thorough testing
   5. Include tickets for unit testing and integration testing
   6. Create a file to inlcude testing instructions
7. Implement beads1-nfp
   8. utilize object oriented programming
   9. after each ticket stop and allow me to create a pull request and share it with you




### Beads Setup

```
cd your-project
bd init
bd setup claude
```

In case of database errors
```
pkill -f "bd|dolt"
```

### Beads UI Setup

https://github.com/mantoni/beads-ui
```
npm i beads-ui -g
# In your project directory:
bdui start --open
```

### beads-dashboard

```
npm install -g beads-dashboard
```


### Environment Setup

#### Create Environment
```aiignore
python3 -m venv .venv
```
#### Activate Environment
```aiignore
source .venv/bin/activate
```
#### Requirements

```aiignore
pip freeze > requirements.txt
pip install -r requirements.txt
```

### Prerequisites

List any software or libraries needed to run the project.
* [Node.js](https://nodejs.org) (v14.0 or higher)
* [npm](https://www.npmjs.com)

### Installation

Step-by-step instructions to get the development environment running.

```bash
# Clone the repository
git clone https://github.com

# Navigate to the project directory
cd project-name

# Install dependencies
npm install
