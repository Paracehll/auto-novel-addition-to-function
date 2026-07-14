import type { Glossary } from './Glossary';

export interface GlobalGlossaryDiffItem {
  old: string | null;
  new: string | null;
}

export interface GlobalGlossaryRecord {
  date: string;
  diff: { [key: string]: GlobalGlossaryDiffItem };
}

export interface GlobalGlossary {
  id: string;
  uid: string;
  name: string;
  content: Glossary;
  used: string[];
  update: string;
  tag: string[];
  record: GlobalGlossaryRecord[];
}
