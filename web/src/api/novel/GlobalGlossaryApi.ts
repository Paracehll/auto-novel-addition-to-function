import type { GlobalGlossary } from '@/model/GlobalGlossary';
import { client } from './client';

const listGlobalGlossaries = () =>
  client.get('global-glossary').json<GlobalGlossary[]>();

const getGlobalGlossary = (uid: string) =>
  client.get(`global-glossary/${uid}`).json<GlobalGlossary>();

const createGlobalGlossary = (json: {
  name: string;
  content: { [key: string]: string };
  tag: string[];
}) => client.post('global-glossary', { json }).json<GlobalGlossary>();

const updateGlobalGlossary = (
  uid: string,
  json: {
    name: string;
    content: { [key: string]: string };
    tag: string[];
  },
) => client.put(`global-glossary/${uid}`, { json }).json<GlobalGlossary>();

const deleteGlobalGlossary = (uid: string) =>
  client.delete(`global-glossary/${uid}`);

const deleteGlobalGlossaryRecord = (uid: string, index: number) =>
  client.delete(`global-glossary/${uid}/record/${index}`);

export const GlobalGlossaryApi = {
  listGlobalGlossaries,
  getGlobalGlossary,
  createGlobalGlossary,
  updateGlobalGlossary,
  deleteGlobalGlossary,
  deleteGlobalGlossaryRecord,
};
